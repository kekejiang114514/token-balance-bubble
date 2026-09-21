package com.coco.balancebubble;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import java.util.Random;

/**
 * 骨架式角色：鲸鱼娘。
 *
 * <p>不引入任何位图素材，全部部件（身体 / 肚皮 / 眼睛 / 腮红 / 嘴 / 左右鳍 / 尾鳍 / 脚）
 * 都在代码里用 Path 画出来，因此可以按「关节」做层级变换：
 * 鳍绕肩点旋转＝挥手，眼睑压缩＝眨眼，身体缩放＝呼吸，尾鳍绕尾根摆动＝游动。
 *
 * <p>姿势每帧由时间戳现算（无状态动画），所以随时可以打断、切换动作。
 */
public class PetView extends View {

    private final Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint whitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eyePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blushPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mouthPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF oval = new RectF();
    private final Random random = new Random();

    /** 动画目标帧间隔（毫秒）。 */
    private static final long FRAME_MS = 33L;

    private final float density;
    private LinearGradient bodyGradient;   // 随尺寸变化，缓存一次

    private boolean animated = true;
    private long born = System.currentTimeMillis();
    private long blinkAt = 0, blinkUntil = 0;
    private long actionStart = 0, actionEnd = 0;
    private PetAction action = PetAction.IDLE;
    private long nextIdleAt = 0;

    public PetView(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        bodyPaint.setStyle(Paint.Style.FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dp(1.6f));
        strokePaint.setColor(0xFF2C3E63);
        whitePaint.setStyle(Paint.Style.FILL);
        whitePaint.setColor(0xFFF2F6FC);
        eyePaint.setStyle(Paint.Style.FILL);
        eyePaint.setColor(0xFF22314F);
        highlightPaint.setStyle(Paint.Style.FILL);
        highlightPaint.setColor(0xFFFFFFFF);
        blushPaint.setStyle(Paint.Style.FILL);
        blushPaint.setColor(0x3DEE8A96);
        mouthPaint.setStyle(Paint.Style.STROKE);
        mouthPaint.setStrokeWidth(dp(1.8f));
        mouthPaint.setStrokeCap(Paint.Cap.ROUND);
        mouthPaint.setColor(0xFF2C3E63);
    }

    private float dp(float v) {
        return v * density;
    }

    /** 关掉动画可以让角色静止站立（省电）。 */
    public void setAnimated(boolean v) {
        if (animated == v) return;
        animated = v;
        invalidate();
        if (v) postInvalidateOnAnimation();
    }

    public boolean isAnimated() {
        return animated;
    }

    /** 外部触发一个动作（会打断当前动作）。 */
    public void play(PetAction a) {
        if (a == null || a == PetAction.IDLE) {
            action = PetAction.IDLE;
            actionEnd = 0;
            return;
        }
        long now = System.currentTimeMillis();
        action = a;
        actionStart = now;
        actionEnd = now + a.duration();
        if (animated) postInvalidateOnAnimation();
    }

    public PetAction currentAction() {
        return System.currentTimeMillis() < actionEnd ? action : PetAction.IDLE;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = resolveSize((int) dp(96), wSpec);
        setMeasuredDimension(w, w);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        bodyGradient = null;   // 渐变坐标跟尺寸绑定，尺寸变了要重建
    }

    /** 一帧要用到的全部姿势参数，单位是「相对于角色边长 S 的比例」或角度。 */
    private static class Pose {
        float rootDy;      // 整体上下位移
        float rootDx;      // 整体左右位移
        float tilt;        // 整体倾斜（度），绕脚底
        float bodySx = 1f;
        float bodySy = 1f;
        float tail;        // 尾鳍角度
        float armL;        // 左鳍角度（0 = 自然下垂）
        float armR;
        float eyeOpen = 1f;
        float pupilDx;     // 瞳孔左右偏移
        float mouth;       // 张嘴程度 0..1
        float blush = 1f;  // 腮红浓度
    }

    /** 当前时间对应的完整姿势：待机动画＋正在播放的动作。 */
    Pose poseAt(long now) {
        Pose p = new Pose();
        float t = (now - born) / 1000f;
        // 待机呼吸
        float breath = (float) Math.sin(t * 1.7f);
        p.bodySy = 1f + 0.020f * breath;
        p.bodySx = 1f - 0.014f * breath;
        p.rootDy = -0.006f * Math.max(0f, breath);
        p.tail = (float) (7 * Math.sin(t * 1.1f));
        p.armL = (float) (7 * Math.sin(t * 1.1f + 0.7f));
        p.armR = -(float) (7 * Math.sin(t * 1.1f + 0.7f));

        // 眨眼
        if (blinkUntil > now) {
            float f = (blinkUntil - now) / 150f;       // 1 → 0
            p.eyeOpen = Math.max(0f, Math.min(1f, 1f - (float) Math.sin(f * Math.PI) * 1.4f));
        } else {
            p.eyeOpen = 1f;
        }

        if (now < actionEnd) {
            float f = (now - actionStart) / (float) Math.max(1, action.duration());
            applyAction(p, action, f);
        }
        return p;
    }

    /** 把动作在进度 f（0..1）处的姿态叠加到 p 上。 */
    private void applyAction(Pose p, PetAction a, float f) {
        float wave = (float) Math.sin(f * Math.PI * 2);
        switch (a) {
            case WAVE: {
                float env = envelope(f, 0.18f, 0.72f);
                p.armR = -132f * env + 22f * env * (float) Math.sin(f * Math.PI * 6);
                p.armL = 10f * env;
                p.tilt = 5f * env;
                break;
            }
            case HAPPY: {
                float env = envelope(f, 0.15f, 0.75f);
                p.armL = -96f * env;
                p.armR = 96f * env;
                p.rootDy = -0.09f * (float) Math.sin(Math.min(1f, f * 1.5f) * Math.PI);
                p.bodySy = 1f + 0.06f * env;
                p.blush = 1.6f;
                break;
            }
            case NOD: {
                p.rootDy = -0.028f * (float) Math.abs(Math.sin(f * Math.PI * 2));
                p.bodySy = 1f - 0.035f * (float) Math.abs(Math.sin(f * Math.PI));
                p.eyeOpen = 1f - 0.5f * (float) Math.abs(Math.sin(f * Math.PI));
                break;
            }
            case SHAKE: {
                p.tilt = 8f * (float) Math.sin(f * Math.PI * 6) * envelope(f, 0.15f, 0.8f);
                break;
            }
            case JUMP: {
                float air = (float) Math.sin(f * Math.PI);
                p.rootDy = -0.13f * air;
                p.bodySy = 1f + 0.10f * air - 0.10f * Math.max(0f, 1f - f / 0.12f);
                p.bodySx = 1f - 0.07f * air;
                p.tail = -18f * air;
                p.armL = -30f * air;
                p.armR = 30f * air;
                break;
            }
            case SWIM: {
                p.tail = 26f * (float) Math.sin(f * Math.PI * 6);
                p.tilt = 5f * (float) Math.sin(f * Math.PI * 4);
                p.rootDx = 0.012f * (float) Math.sin(f * Math.PI * 4);
                p.armL = 22f * (float) Math.sin(f * Math.PI * 6);
                p.armR = -22f * (float) Math.sin(f * Math.PI * 6);
                break;
            }
            case SLEEPY: {
                float sleep = (float) Math.sin(f * Math.PI);
                p.eyeOpen = Math.min(p.eyeOpen, 1f - 0.94f * sleep);
                p.bodySy = 1f + 0.03f * sleep;
                p.tail = 3f * (float) Math.sin(f * Math.PI * 2);
                break;
            }
            case LOOK: {
                p.pupilDx = 0.020f * wave;
                p.tilt = 4f * wave;
                break;
            }
            case SURPRISE: {
                float s = (float) Math.sin(f * Math.PI);
                p.eyeOpen = 1f;
                p.rootDy = -0.05f * s;
                p.bodySy = 1f + 0.07f * s;
                p.mouth = s;
                p.armL = -52f * s;
                p.armR = 52f * s;
                break;
            }
            default:
                break;
        }
    }

    /** 动作的进出包络：开头淡入、结尾淡出，中间为 1。 */
    private static float envelope(float f, float in, float outStart) {
        if (f < in) return f / in;
        if (f > outStart) return Math.max(0f, (1f - f) / (1f - outStart));
        return 1f;
    }

    /** 按时间推进待机小动作与眨眼。 */
    private void schedule(long now) {
        if (!animated) return;
        if (nextIdleAt == 0) nextIdleAt = now + 2500 + random.nextInt(3000);
        if (now >= nextIdleAt && now >= actionEnd) {
            PetAction[] pool = PetAction.idlePool();
            play(pool[random.nextInt(pool.length)]);
            nextIdleAt = now + 3500 + random.nextInt(4000);
        }
        if (blinkAt == 0) blinkAt = now + 1500 + random.nextInt(3000);
        if (now >= blinkAt && blinkUntil <= now) {
            blinkUntil = now + 150;
            blinkAt = now + 2200 + random.nextInt(3800);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long now = System.currentTimeMillis();
        schedule(now);
        float S = Math.min(getWidth(), getHeight());
        if (S <= 0) return;
        if (bodyGradient == null) {
            bodyGradient = new LinearGradient(0, 0, 0, S, 0xFF74A2D8, 0xFF3B63A4, Shader.TileMode.CLAMP);
            bodyPaint.setShader(bodyGradient);
        }
        Pose p = poseAt(now);

        canvas.save();
        canvas.translate((getWidth() - S) / 2f, (getHeight() - S) / 2f);
        canvas.translate(p.rootDx * S, p.rootDy * S);
        canvas.rotate(p.tilt, 0.5f * S, 0.94f * S);

        drawTail(canvas, S, p);
        drawFeet(canvas, S);
        drawBody(canvas, S, p);
        drawArms(canvas, S, p);

        canvas.restore();

        // 省电：限到 30fps，动画看不出差别，重绘次数少一半
        if (animated && isShown()) {
            postInvalidateDelayed(FRAME_MS);
        }
    }

    // ---------------- 各部件 ----------------

    private void drawTail(Canvas canvas, float S, Pose p) {
        canvas.save();
        canvas.translate(0.5f * S, 0.84f * S);
        canvas.rotate(p.tail);
        path.reset();
        path.moveTo(0, -0.02f * S);
        path.quadTo(-0.16f * S, 0.02f * S, -0.20f * S, 0.13f * S);
        path.quadTo(-0.10f * S, 0.11f * S, 0, 0.11f * S);
        path.quadTo(0.10f * S, 0.11f * S, 0.20f * S, 0.13f * S);
        path.quadTo(0.16f * S, 0.02f * S, 0, -0.02f * S);
        path.close();
        canvas.drawPath(path, bodyPaint);
        canvas.drawPath(path, strokePaint);
        canvas.restore();
    }

    private void drawFeet(Canvas canvas, float S) {
        oval.set(0.325f * S, 0.855f * S, 0.465f * S, 0.945f * S);
        canvas.drawOval(oval, bodyPaint);
        canvas.drawOval(oval, strokePaint);
        oval.set(0.535f * S, 0.855f * S, 0.675f * S, 0.945f * S);
        canvas.drawOval(oval, bodyPaint);
        canvas.drawOval(oval, strokePaint);
    }

    private void drawBody(Canvas canvas, float S, Pose p) {
        float cx = 0.5f * S, cy = 0.58f * S;
        float rx = 0.40f * S, ry = 0.30f * S;
        canvas.save();
        canvas.scale(p.bodySx, p.bodySy, cx, cy);

        oval.set(cx - rx, cy - ry, cx + rx, cy + ry);
        canvas.drawOval(oval, bodyPaint);
        canvas.drawOval(oval, strokePaint);

        // 头顶小水柱
        mouthPaint.setColor(0xFF6EA8E8);
        oval.set(0.465f * S, 0.215f * S, 0.535f * S, 0.295f * S);
        canvas.drawArc(oval, 200, 140, false, mouthPaint);
        mouthPaint.setColor(0xFF2C3E63);

        // 肚皮
        oval.set(cx - 0.275f * S, cy - 0.155f * S, cx + 0.275f * S, cy + 0.270f * S);
        canvas.drawOval(oval, whitePaint);

        // 腮红
        blushPaint.setAlpha(Math.min(255, (int) (0x3D * Math.min(2f, p.blush))));
        oval.set(cx - 0.28f * S, cy - 0.005f * S, cx - 0.13f * S, cy + 0.070f * S);
        canvas.drawOval(oval, blushPaint);
        oval.set(cx + 0.13f * S, cy - 0.005f * S, cx + 0.28f * S, cy + 0.070f * S);
        canvas.drawOval(oval, blushPaint);

        drawEye(canvas, S, cx - 0.125f * S, cy - 0.062f * S, p);
        drawEye(canvas, S, cx + 0.125f * S, cy - 0.062f * S, p);

        // 嘴
        float mw = 0.055f * S + 0.05f * S * p.mouth;
        oval.set(cx - mw, cy + 0.045f * S, cx + mw, cy + 0.100f * S);
        canvas.drawArc(oval, 0, 180, false, mouthPaint);

        canvas.restore();
    }

    /** 一只眼睛：眼白底 + 眼珠 + 高光；闭眼时画成一条弧线。 */
    private void drawEye(Canvas canvas, float S, float ex, float ey, Pose p) {
        float r = 0.056f * S;
        if (p.eyeOpen < 0.14f) {
            oval.set(ex - r, ey - r * 0.9f, ex + r, ey + r * 0.9f);
            canvas.drawArc(oval, 200, 140, false, mouthPaint);
            return;
        }
        canvas.save();
        canvas.scale(1f, p.eyeOpen, ex, ey);
        whitePaint.setColor(0xFFFFFFFF);
        oval.set(ex - r, ey - r, ex + r, ey + r);
        canvas.drawOval(oval, whitePaint);
        whitePaint.setColor(0xFFF2F6FC);
        float px = ex + p.pupilDx * S;
        oval.set(px - r * 0.62f, ey - r * 0.66f + 0.004f * S, px + r * 0.62f, ey + r * 0.66f + 0.004f * S);
        canvas.drawOval(oval, eyePaint);
        oval.set(px - r * 0.42f, ey - r * 0.5f, px - r * 0.02f, ey - r * 0.08f);
        canvas.drawOval(oval, highlightPaint);
        canvas.restore();
    }

    private void drawArms(Canvas canvas, float S, Pose p) {
        drawFlipper(canvas, S, 0.120f * S, 0.555f * S, p.armL, -1f);
        drawFlipper(canvas, S, 0.880f * S, 0.555f * S, p.armR, 1f);
    }

    /** 一只胸鳍：肩点是旋转轴，鳍身朝下（角度 0），正角度往外/往上摆。 */
    private void drawFlipper(Canvas canvas, float S, float sx, float sy, float angle, float dir) {
        canvas.save();
        canvas.translate(sx, sy);
        canvas.rotate(angle * dir);
        path.reset();
        path.moveTo(0, 0);
        path.quadTo(dir * 0.075f * S, 0.06f * S, dir * 0.085f * S, 0.185f * S);
        path.quadTo(dir * 0.045f * S, 0.155f * S, 0, 0.155f * S);
        path.quadTo(-dir * 0.045f * S, 0.185f * S, -dir * 0.05f * S, 0.10f * S);
        path.quadTo(-dir * 0.03f * S, 0.03f * S, 0, 0);
        path.close();
        canvas.drawPath(path, bodyPaint);
        canvas.drawPath(path, strokePaint);
        canvas.restore();
    }
}

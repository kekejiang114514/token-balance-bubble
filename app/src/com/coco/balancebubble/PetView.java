package com.coco.balancebubble;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.view.View;

import java.io.InputStream;
import java.util.Random;

/**
 * 角色视图：把 assets/char.png 画出来，并让它动起来。
 *
 * <p>这是「整体骨架」式的做法——角色只有一张整图，不能把手臂单独拆出来转，
 * 所以动作是围绕「脚底着地点」这一个支点做的整体姿态变换：
 * 位移（浮沉、蹦跳）、旋转（歪头、摇头、转圈）、缩放（呼吸、蹲下、伸懒腰、落地挤压）。
 * 支点固定在脚底，所以挤压时脚不会离地，转圈时是原地转，看起来才像有重心的角色。
 * 再配一个随高度变化的地面投影，跳起来才有腾空感。
 *
 * <p>想让它真正「挥手、眨眼」，需要把角色拆成部件图层（手臂、眼睑等），
 * 单张整图做不到，这点在 README 里有说明。
 */
public class PetView extends View {

    /** 一帧的姿态参数。 */
    private static final class Pose {
        float dx;       // 水平位移（相对宽度）
        float dy;       // 垂直位移（相对宽度，负＝向上）
        float rot;      // 旋转角度
        float sx = 1f;  // 横向缩放
        float sy = 1f;  // 纵向缩放
        float pivotY = 0f; // 旋转支点（0＝脚底，0.5＝贴图中心）
    }

    /** 贴图占视图宽度的比例：留出倾斜/蹦跳的余量，正好卡在不被裁掉的上限。 */
    private static final float FIT = 0.82f;
    /** 视图高度 = VIEW_H_RATIO × 宽度，多出来的就是起跳净空。 */
    public static final float VIEW_H_RATIO = 1.15f;
    /** 旋转角度上限：实测超过 15° 贴图两侧会被视图裁掉。 */
    private static final float MAX_TILT = 12f;

    private static final long FRAME_MS = 33L;      // 约 30fps
    private static final long IDLE_MIN_MS = 3200L; // 待机随机动作间隔
    private static final long IDLE_MAX_MS = 7200L;

    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF shadowRect = new RectF();
    private final RectF dst = new RectF();
    private final Matrix matrix = new Matrix();
    private final Pose pose = new Pose();
    private final Random random = new Random();

    private Bitmap source;      // 原始贴图
    private Bitmap scaled;      // 按当前尺寸预缩放的贴图
    private int scaledFor = -1; // scaled 对应的视图宽度，尺寸变了要重建
    private boolean animated = true;

    private PetAction action = PetAction.IDLE;
    private long actionStart = 0;
    private long actionEnd = 0;
    private long nextIdleAt = 0;

    public PetView(Context context) {
        super(context);
        load(context);
        shadowPaint.setColor(0x33000000);
    }

    private void load(Context context) {
        try {
            InputStream is = context.getAssets().open("char.png");
            source = BitmapFactory.decodeStream(is);
            is.close();
        } catch (Exception e) {
            source = null;
        }
    }

    public void setAnimated(boolean on) {
        if (animated == on) return;
        animated = on;
        if (on) {
            play(PetAction.IDLE);
        }
        invalidate();
    }

    public boolean isAnimated() {
        return animated;
    }

    /** 播放一个动作；IDLE 表示停下当前动作。 */
    public void play(PetAction a) {
        if (a == null || a == PetAction.IDLE) {
            action = PetAction.IDLE;
            actionStart = actionEnd = 0;
            return;
        }
        long now = System.currentTimeMillis();
        action = a;
        actionStart = now;
        actionEnd = now + a.duration();
    }

    public PetAction currentAction() {
        return System.currentTimeMillis() < actionEnd ? action : PetAction.IDLE;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        scaledFor = -1;   // 尺寸变了，预缩放作废
        scaled = null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (source == null) return;

        final int w = getWidth();
        final int h = getHeight();
        if (w <= 0 || h <= 0) return;
        ensureScaled(w);

        long now = System.currentTimeMillis();
        if (nextIdleAt == 0) nextIdleAt = now + IDLE_MIN_MS + random.nextInt(1200);

        computePose(now);

        // 地面支点：角色脚底站在这里
        float groundY = h * 0.97f;
        float cx = w * 0.5f;
        float dw = scaled.getWidth();
        float dh = scaled.getHeight();

        // 地面投影：抬得越高，影子越小越淡
        float lift = Math.max(0f, -pose.dy) * w;
        float base = w * 0.34f * pose.sx;
        float shrink = Math.max(0.35f, 1f - lift / (w * 0.55f));
        float sw = base * shrink;
        float sh = Math.max(3f, h * 0.035f * shrink);
        shadowRect.set(cx - sw / 2f, groundY - sh / 2f, cx + sw / 2f, groundY + sh / 2f);
        shadowPaint.setAlpha((int) (0x42 * shrink));
        canvas.drawOval(shadowRect, shadowPaint);

        canvas.save();
        canvas.translate(cx + pose.dx * w, groundY + pose.dy * w);
        if (pose.rot != 0f) {
            // 自转绕贴图中心（绕脚底转会甩出视图），倾斜仍绕脚底，重心才稳
            float py = -dh * pose.pivotY;
            canvas.translate(0f, py);
            canvas.rotate(pose.rot);
            canvas.translate(0f, -py);
        }
        if (pose.sx != 1f || pose.sy != 1f) canvas.scale(pose.sx, pose.sy);
        dst.set(-dw / 2f, -dh, dw / 2f, 0f);
        canvas.drawBitmap(scaled, null, dst, bitmapPaint);
        canvas.restore();

        if (animated && isShown()) {
            postInvalidateDelayed(FRAME_MS);
        }
    }

    /** 尺寸变了才重建缩放缓存，避免每帧用 shader 采样导致边缘发虚。 */
    private void ensureScaled(int viewW) {
        int target = Math.max(48, Math.round(viewW * FIT));
        if (scaled != null && scaledFor == target) return;
        float k = target / (float) source.getWidth();
        int tw = target;
        int th = Math.max(1, Math.round(source.getHeight() * k));
        // 只在缩小时预缩放，放大会保留原图质量
        if (tw <= source.getWidth()) {
            Bitmap b = Bitmap.createScaledBitmap(source, tw, th, true);
            if (scaled != null && scaled != b && !scaled.isRecycled()) scaled.recycle();
            scaled = b;
        } else {
            scaled = source;
        }
        scaledFor = target;
    }

    /** 把当前动作换算成这一帧的姿态。 */
    private void computePose(long now) {
        pose.dx = 0f;
        pose.dy = 0f;
        pose.rot = 0f;
        pose.sx = 1f;
        pose.sy = 1f;
        pose.pivotY = 0f;

        float t = now / 1000f;
        // 基础呼吸：即使静止也在轻轻起伏，避免看起来是张死图
        float breathe = (float) Math.sin(t * 2.0f * Math.PI / 2.9f);
        pose.sy *= 1f + 0.010f * breathe;
        pose.dy -= 0.006f * (1f + breathe);

        if (!animated) {
            pose.sx = 1f;
            pose.sy = 1f;
            pose.dy = 0f;
            return;
        }

        if (action == PetAction.IDLE || now >= actionEnd) {
            if (action != PetAction.IDLE) {
                action = PetAction.IDLE;
            }
            if (now >= nextIdleAt) {
                PetAction[] pool = PetAction.idlePool();
                play(pool[random.nextInt(pool.length)]);
                nextIdleAt = now + IDLE_MIN_MS + random.nextInt((int) (IDLE_MAX_MS - IDLE_MIN_MS));
            }
            return;
        }

        float f = (now - actionStart) / (float) Math.max(1L, action.duration());
        f = Math.max(0f, Math.min(1f, f));
        switch (action) {
            case FLOAT: {
                float s = (float) Math.sin(f * 2f * (float) Math.PI);
                pose.dy -= 0.05f * s;
                pose.sy *= 1f + 0.02f * s;
                break;
            }
            case BOUNCE: {
                // 前半段上升，后半段落下并在落地时挤压
                float up = f < 0.45f ? f / 0.45f : (1f - f) / 0.55f;
                float hgt = (float) Math.sin(Math.min(1f, up) * Math.PI / 2f);
                pose.dy -= 0.16f * hgt;
                if (f > 0.88f) {
                    float k = (f - 0.88f) / 0.12f;
                    pose.sy *= 1f - 0.12f * (1f - k);
                    pose.sx *= 1f + 0.09f * (1f - k);
                }
                break;
            }
            case SWAY: {
                pose.rot = 5f * (float) Math.sin(f * 3f * Math.PI);
                pose.dx = 0.012f * (float) Math.sin(f * 3f * Math.PI);
                break;
            }
            case LEAN: {
                // 歪过去，停一会儿，再回来
                float k = f < 0.25f ? f / 0.25f : (f > 0.75f ? (1f - f) / 0.25f : 1f);
                pose.rot = 10f * k;
                pose.dx = 0.012f * k;
                break;
            }
            case SHAKE: {
                pose.rot = 7f * (float) Math.sin(f * 6f * Math.PI) * (1f - f);
                break;
            }
            case NOD: {
                float k = (float) Math.sin(f * 2f * Math.PI);
                pose.dy += 0.028f * Math.max(0f, k);
                pose.sy *= 1f - 0.02f * Math.max(0f, k);
                break;
            }
            case SPIN: {
                float e = f * f * (3f - 2f * f);   // 缓入缓出
                pose.rot = 360f * e;
                pose.pivotY = 0.5f;                // 原地转圈：绕贴图中心
                pose.dy -= 0.06f * (float) Math.sin(f * Math.PI);
                break;
            }
            case STRETCH: {
                float k = f < 0.4f ? f / 0.4f : (f > 0.7f ? (1f - f) / 0.3f : 1f);
                pose.sy *= 1f + 0.10f * k;
                pose.sx *= 1f - 0.06f * k;
                pose.dy -= 0.03f * k;
                break;
            }
            case DUCK: {
                float k = f < 0.35f ? f / 0.35f : (f > 0.7f ? (1f - f) / 0.3f : 1f);
                pose.sy *= 1f - 0.09f * k;
                pose.sx *= 1f + 0.06f * k;
                break;
            }
            case POP: {
                // 快速弹一下：先缩后弹
                float k = f < 0.3f ? -0.6f * (1f - f / 0.3f) : (float) Math.sin((f - 0.3f) / 0.7f * Math.PI);
                pose.sy *= 1f + 0.14f * k;
                pose.sx *= 1f - 0.10f * k;
                pose.dy -= 0.05f * Math.max(0f, k);
                break;
            }
            case SLEEPY: {
                float k = (float) Math.sin(f * Math.PI);
                pose.dy += 0.035f * k;
                pose.sy *= 1f - 0.04f * k;
                pose.rot = 5f * k;
                break;
            }
            default:
                break;
        }
        // 统一兜底：任何动作都不许把贴图甩出视图
        if (pose.rot > MAX_TILT && pose.rot < 360f - MAX_TILT && pose.pivotY == 0f) {
            pose.rot = Math.max(-MAX_TILT, Math.min(MAX_TILT, pose.rot));
        }
    }
}

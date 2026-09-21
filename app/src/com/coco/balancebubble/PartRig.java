package com.coco.balancebubble;

/**
 * 部件级动画：把一整张贴图铺成网格，按区域给每个网格顶点加位移。
 *
 * <p>角色只有一张平面立绘，没有分层，所以做不到"单独旋转手臂"。
 * 这里改用网格形变（Android 端配合 {@code Canvas.drawBitmapMesh}）：
 * <ul>
 *   <li><b>眨眼</b>：眼睛区域的顶点向卧蚕线塌陷，眨眼时眼睛被压成一条线；</li>
 *   <li><b>挥手</b>：手部区域绕腕点旋转，软权重让袖口不动；</li>
 *   <li><b>甩尾</b>：两侧长发（尾部）绕根部摆动；</li>
 *   <li><b>头发随风</b>：头部外缘按正弦波纹横向漂移，风有强弱起伏。</li>
 * </ul>
 *
 * <p>所有坐标都用"原始贴图像素"（1022×1062）描述，最后按视图尺寸线性换算，
 * 因此角色放大缩小时动作幅度等比缩放。纯逻辑、不依赖 Android，可直接做 JVM 单测。
 */
public final class PartRig {

    /** 贴图原始尺寸，所有区域常量都按这套坐标写。 */
    public static final float RAW_W = 1022f;
    public static final float RAW_H = 1062f;

    /** 网格段数：26×26 够平滑，每帧 729 个顶点的位移计算量可忽略。 */
    public static final int MW = 26;
    public static final int MH = 26;

    // ---------------- 区域常量（原始贴图像素，由原图分析得到） ----------------

    /** 左眼：外接盒 x291-379, y438-550。 */
    static final float EYE_L_CX = 335f, EYE_L_CY = 494f, EYE_L_HW = 44f, EYE_L_HH = 56f;
    /** 右眼：外接盒 x492-588, y432-548。 */
    static final float EYE_R_CX = 540f, EYE_R_CY = 490f, EYE_R_HW = 48f, EYE_R_HH = 58f;
    /** 眨眼塌陷到的"卧蚕线"（眼睛下缘），比眼睛中心略低。 */
    static final float EYE_CLOSE_Y = 536f;
    /** 眨眼时竖直方向最多压掉的比例。 */
    static final float BLINK_SQUASH = 0.90f;

    /** 左手（画面左）：x282-350, y696-770，腕点在手指上方。 */
    static final float HAND_L_CX = 316f, HAND_L_CY = 733f, HAND_L_HW = 34f, HAND_L_HH = 37f;
    static final float HAND_L_PX = 316f, HAND_L_PY = 700f;
    /** 右手（画面右）：x524-592, y693-767。 */
    static final float HAND_R_CX = 558f, HAND_R_CY = 730f, HAND_R_HW = 34f, HAND_R_HH = 37f;
    static final float HAND_R_PX = 558f, HAND_R_PY = 697f;

    /** 左侧长发/尾：绕根部摆动，根部枢轴 (150, 620)。 */
    // 两侧长发：X1 = 发丝外侧（权重饱和），X0 = 内侧边界（权重 0 的一侧）；Y0 = 开始摆动的y
    // 甩尾改成有界横摆后不再需要「根部支点」，过渡长度由 TAIL_X_FADE / TAIL_Y_RISE 决定
    static final float TAIL_L_X0 = 215f, TAIL_L_X1 = 300f;
    static final float TAIL_L_Y0 = 600f, TAIL_L_Y1 = 720f;
    static final float TAIL_R_X0 = 700f, TAIL_R_X1 = 785f;
    static final float TAIL_R_Y0 = 580f, TAIL_R_Y1 = 700f;

    /** 脸部保护区：头发/尾巴的权重在这里被抵消，避免把脸和五官带歪。 */
    static final float FACE_CX = 438f, FACE_CY = 505f, FACE_RX = 205f, FACE_RY = 155f;

    /** 头部随风波纹：以头顶为圆心的一圈，只作用在眼睛以上。 */
    static final float HEAD_CX = 438f, HEAD_CY = 300f;
    static final float HEAD_R0 = 170f, HEAD_R1 = 360f;
    static final float HEAD_Y_CUT = 470f;

    /** 幅度（原始贴图像素）。 */
    static final float WIND_AMP = 17f;      // 头部波纹
    static final float WIND_TAIL_AMP = 26f; // 长发随风漂移
    static final float WAG_AMP_DEG = 15f;   // 甩尾的参考幅度（满幅=15 度，测试接口沿用度数）
    static final float WAG_SWEEP_PX = 30f;  // 满幅甩尾对应的横向位移
    static final float WAVE_AMP_DEG = 27f;  // 挥手角
    static final float TAIL_X_FADE = 110f;  // 发丝横向影响带宽度
    static final float TAIL_Y_RISE = 130f;  // 由根到梢的过渡长度
    static final float TAIL_SWEEP_R = 0.85f; // 右侧摆幅略小，避免镜像感
    static final float IDLE_TAIL_DEG = 2.4f;
    static final float IDLE_HAND_DEG = 3.0f;

    /** 眨眼节奏：间隔区间与单次时长。 */
    static final long BLINK_GAP_MIN_MS = 2200L;
    static final long BLINK_GAP_MAX_MS = 6200L;
    static final long BLINK_MS = 190L;

    private final float[] verts = new float[(MW + 1) * (MH + 1) * 2];
    private final float[] ui = new float[MW + 1];
    private final float[] vj = new float[MH + 1];

    private final java.util.Random random = new java.util.Random();

    private long blinkStart = -1L;
    private long nextBlinkAt;
    private boolean doubleBlink;

    /** 本帧的所有位移是否都可忽略（可走直接 drawBitmap 的快路径）。 */
    private boolean neutral = true;

    public PartRig() {
        for (int i = 0; i <= MW; i++) ui[i] = i / (float) MW;
        for (int j = 0; j <= MH; j++) vj[j] = j / (float) MH;
    }

    // ---------------- 权重场（纯函数，可单测） ----------------

    static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    /** smoothstep，让权重在边界处没有折角，形变不会出现硬边。 */
    static float smooth(float v) {
        v = clamp01(v);
        return v * v * (3f - 2f * v);
    }

    /** 软矩形权重：盒内为 1，向外 margin 像素内平滑降到 0。 */
    public static float box(float x, float y, float cx, float cy, float hw, float hh, float margin) {
        float fx = clamp01((hw + margin - Math.abs(x - cx)) / margin);
        float fy = clamp01((hh + margin - Math.abs(y - cy)) / margin);
        return smooth(Math.min(fx, fy));
    }

    /** 椭圆权重，边界外 margin（相对半径的比例）内平滑衰减。 */
    public static float ellipse(float x, float y, float cx, float cy, float rx, float ry, float margin) {
        float nx = (x - cx) / rx, ny = (y - cy) / ry;
        float d = (float) Math.sqrt(nx * nx + ny * ny);
        return smooth((1f + margin - d) / margin);
    }

    /** 脸部与两只手的保护区：头发、尾巴的权重在这里被抵消。 */
    static float protect(float x, float y) {
        float p = ellipse(x, y, FACE_CX, FACE_CY, FACE_RX, FACE_RY, 0.30f);
        float hl = box(x, y, HAND_L_CX, HAND_L_CY, HAND_L_HW, HAND_L_HH, 14f);
        if (hl > p) p = hl;
        float hr = box(x, y, HAND_R_CX, HAND_R_CY, HAND_R_HW, HAND_R_HH, 14f);
        if (hr > p) p = hr;
        return p;
    }

    /** 左侧长发/尾（尾部）权重，已扣掉脸部与手。 */
    public static float weightTailLeft(float x, float y) {
        float wx = smooth((TAIL_L_X1 - x) / TAIL_X_FADE);
        float wy = smooth((y - TAIL_L_Y0) / TAIL_Y_RISE);
        return Math.max(0f, wx * wy - 1.25f * protect(x, y));
    }

    /** 右侧长发/尾（尾部）权重。 */
    public static float weightTailRight(float x, float y) {
        float wx = smooth((x - TAIL_R_X0) / TAIL_X_FADE);
        float wy = smooth((y - TAIL_R_Y0) / TAIL_Y_RISE);
        return Math.max(0f, wx * wy - 1.25f * protect(x, y));
    }

    /** 头部随风波纹权重：头顶外圈为 1、头部中心为 0，只在眼睛以上生效。 */
    public static float weightHead(float x, float y) {
        float dx = x - HEAD_CX, dy = y - HEAD_CY;
        float r = (float) Math.sqrt(dx * dx + dy * dy);
        float w = smooth((r - HEAD_R0) / (HEAD_R1 - HEAD_R0));
        float fy = smooth((HEAD_Y_CUT - y) / 80f);
        return Math.max(0f, w * fy - 1.25f * protect(x, y));
    }

    /** 左眼权重。 */
    public static float weightEyeLeft(float x, float y) {
        return box(x, y, EYE_L_CX, EYE_L_CY, EYE_L_HW, EYE_L_HH, 24f);
    }

    /** 右眼权重。 */
    public static float weightEyeRight(float x, float y) {
        return box(x, y, EYE_R_CX, EYE_R_CY, EYE_R_HW, EYE_R_HH, 24f);
    }

    /** 左手权重。 */
    public static float weightHandLeft(float x, float y) {
        return box(x, y, HAND_L_CX, HAND_L_CY, HAND_L_HW, HAND_L_HH, 22f);
    }

    /** 右手权重。 */
    public static float weightHandRight(float x, float y) {
        return box(x, y, HAND_R_CX, HAND_R_CY, HAND_R_HW, HAND_R_HH, 22f);
    }

    // ---------------- 时间驱动 ----------------

    /** 眨眼进度：0 睁开，1 完全闭合。 */
    public float blinkAmount(long now) {
        if (blinkStart < 0L) return 0f;
        long t = now - blinkStart;
        if (t < 0L || t > BLINK_MS) return 0f;
        float f = t / (float) BLINK_MS;
        if (f < 0.34f) return smooth(f / 0.34f);
        if (f < 0.56f) return 1f;
        return 1f - smooth((f - 0.56f) / 0.44f);
    }

    private void scheduleBlink(long now) {
        if (doubleBlink) {
            nextBlinkAt = now + 130L;
            doubleBlink = false;
        } else {
            nextBlinkAt = now + BLINK_GAP_MIN_MS
                    + random.nextInt((int) (BLINK_GAP_MAX_MS - BLINK_GAP_MIN_MS));
            doubleBlink = random.nextInt(100) < 28;
        }
    }

    /** 测试用：把下一次眨眼强制排到指定时刻。 */
    public void forceBlinkAt(long whenMs) {
        blinkStart = -1L;
        nextBlinkAt = whenMs;
    }

    /** 当前帧状态：眨眼量、风强（含阵风）、甩尾角、挥手角（弧度）。 */
    private float blink, wind;
    private float windPhase;
    private float windBoost;
    private float wagNorm, waveRad;   // wagNorm：甩尾强度 -1..1（1 = 满幅）
    private boolean animated;

    /**
     * 推进一帧。
     *
     * @param now        当前时间戳（毫秒）
     * @param action     正在播的动作
     * @param progress   动作进度 0..1，IDLE 传 -1
     * @param animated   是否开启动画
     */
    public void update(long now, PetAction action, float progress, boolean animated) {
        this.animated = animated;
        if (!animated) {
            blink = 0f;
            wind = 0f;
            windBoost = 1f;
            wagNorm = 0f;
            waveRad = 0f;
            neutral = true;
            return;
        }

        if (nextBlinkAt == 0L) scheduleBlink(now);
        if (blinkStart < 0L && now >= nextBlinkAt) blinkStart = now;
        if (blinkStart >= 0L && now - blinkStart > BLINK_MS) {
            blinkStart = -1L;
            scheduleBlink(now);
        }
        blink = blinkAmount(now);

        float t = now / 1000f;
        float gust = 0.55f + 0.45f * (float) Math.sin(2f * Math.PI * t / 9.7f + 0.7f);
        wind = gust;
        windPhase = (float) (2f * Math.PI * t / 3.4f);

        float tailIdle = IDLE_TAIL_DEG * (float) Math.sin(2f * Math.PI * t / 4.6f);
        float handIdle = IDLE_HAND_DEG * (float) Math.sin(2f * Math.PI * t / 5.3f);
        float wagNorm = tailIdle / WAG_AMP_DEG;   // 待机时只是轻轻晃
        float waveDeg = handIdle;
        windBoost = 1f;

        if (progress >= 0f && action != null) {
            if (action == PetAction.WAG) {
                float env = (float) Math.pow(Math.sin(Math.PI * progress), 0.7);
                wagNorm += env * (float) Math.sin(2f * Math.PI * 1.7f * progress);
                windBoost = 1f + 0.6f * env;
            } else if (action == PetAction.WAVE) {
                float env = (float) Math.pow(Math.sin(Math.PI * progress), 0.6);
                waveDeg += WAVE_AMP_DEG * env * (float) Math.sin(2f * Math.PI * 2.4f * progress);
            }
        }
        this.wagNorm = wagNorm;
        waveRad = (float) Math.toRadians(waveDeg);
    }

    // ---------------- 顶点位移 ----------------

    private float ddx, ddy;

    /** 计算 (x,y) 处的像素位移（raw 贴图像素单位），结果写入 ddx/ddy。 */
    void disp(float x, float y) {
        ddx = 0f;
        ddy = 0f;
        if (!animated) return;

        // ① 眨眼：眼睛区域向睑线塌陷，横向略收
        if (blink > 0.001f) {
            float m = weightEyeLeft(x, y);
            if (m > 0f) {
                ddy += (EYE_CLOSE_Y - y) * BLINK_SQUASH * blink * m;
                ddx += (EYE_L_CX - x) * 0.06f * blink * m;
            }
            m = weightEyeRight(x, y);
            if (m > 0f) {
                ddy += (EYE_CLOSE_Y - y) * BLINK_SQUASH * blink * m;
                ddx += (EYE_R_CX - x) * 0.06f * blink * m;
            }
        }

        // ② 头发随风：头部外圈的正弦波纹，越靠外缘越明显
        float hw = weightHead(x, y);
        if (hw > 0f) {
            float s = (float) Math.sin(windPhase - y * 0.0060f) * WIND_AMP * wind * hw;
            ddx += s;
            ddy += s * 0.32f;
        }

        // ③ 两侧长发：随风漂移 + 甩尾（整条发丝朝同一侧摆，幅度封顶）
        addTailField(x, y, weightTailLeft(x, y), 0f, 1f);
        addTailField(x, y, weightTailRight(x, y), -1.1f, TAIL_SWEEP_R);

        // ④ 手：绕腕点旋转（挥手），左手只跟一点点，动作自然些
        addHandField(x, y, weightHandLeft(x, y), HAND_L_PX, HAND_L_PY, waveRad * 0.35f);
        addHandField(x, y, weightHandRight(x, y), HAND_R_PX, HAND_R_PY, waveRad);
    }

    /**
     * 长发：风漂移 + 甩尾。
     * <p>甩尾用「有界横摆」而不是绕根旋转：旋转的位移随离根距离线性增长，长发一直垂到贴图底部，
     * 底端会被拽出上百像素，网格直接被拉翻（实测最小格宽 -26px）。横摆幅度封顶后，
     * 形变梯度始终小于 1，网格不会折叠。
     */
    private void addTailField(float x, float y, float f, float phaseOff, float sweepScale) {
        if (f <= 0f) return;
        float s = (float) Math.sin(windPhase - y * 0.0042f + phaseOff)
                * WIND_TAIL_AMP * wind * windBoost * f;
        ddx += s;
        ddy += s * 0.22f;
        if (wagNorm != 0f) {
            float a = WAG_SWEEP_PX * wagNorm * sweepScale * f;
            ddx -= a;
            ddy -= a * 0.20f;
        }
    }

    private void addHandField(float x, float y, float f, float px, float py, float angle) {
        if (f <= 0f || angle == 0f) return;
        float a = angle * f;
        float ca = (float) Math.cos(a) - 1f, sa = (float) Math.sin(a);
        float rx = x - px, ry = y - py;
        ddx += rx * ca - ry * sa;
        ddy += rx * sa + ry * ca;
    }

    /**
     * 生成网格顶点（view 坐标）。
     *
     * @param dstLeft 贴图在画布上的左边界（未含整体位移/缩放，由调用方用 canvas 变换处理）
     */
    public void fill(float dstLeft, float dstTop, float dstW, float dstH, float[] out) {
        float kx = dstW / RAW_W, ky = dstH / RAW_H;
        boolean any = false;
        int k = 0;
        for (int j = 0; j <= MH; j++) {
            float v = vj[j], y = v * RAW_H;
            for (int i = 0; i <= MW; i++, k += 2) {
                float u = ui[i];
                disp(u * RAW_W, y);
                if (!any && (ddx > 0.3f || ddx < -0.3f || ddy > 0.3f || ddy < -0.3f)) any = true;
                out[k] = dstLeft + u * dstW + ddx * kx;
                out[k + 1] = dstTop + v * dstH + ddy * ky;
            }
        }
        neutral = !any;
    }

    /** 位移可忽略时为 true，调用方可以走普通绘制快路。 */
    /** 指定点（贴图像素坐标）的水平位移，供自检/测试使用。 */
    public float dxAt(float x, float y) {
        disp(x, y);
        return ddx;
    }

    /** 指定点（贴图像素坐标）的垂直位移。 */
    public float dyAt(float x, float y) {
        disp(x, y);
        return ddy;
    }

    /** 当前眨眼闭合度 0..1。 */
    public float blinkNow() {
        return blink;
    }

    /** 自检用：直接指定各驱动量（须在 update 之后、fill 之前调用）。wag/wave 都按「度」传，甩尾满幅 = WAG_AMP_DEG。 */
    public void overrideForTest(float blinkV, float windV, float windPhaseV, float windBoostV,
                                float wagDegV, float waveDegV) {
        blink = blinkV;
        wind = windV;
        windPhase = windPhaseV;
        windBoost = windBoostV;
        wagNorm = Math.max(-1.5f, Math.min(1.5f, wagDegV / WAG_AMP_DEG));
        waveRad = (float) Math.toRadians(waveDegV);
        animated = true;
    }

    public boolean isNeutral() {
        return neutral;
    }

    /** 顶点数组（长度 = (MW+1)*(MH+1)*2）。 */
    public float[] vertices() {
        return verts;
    }

    /** 网格尺寸，供调用方校验。 */
    public static int meshWidth() {
        return MW;
    }

    public static int meshHeight() {
        return MH;
    }
}

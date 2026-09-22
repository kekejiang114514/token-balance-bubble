package com.coco.balancebubble;

import static com.coco.balancebubble.RigModel.*;



/**
 * 骨架：把一张平面立绘铺成 {@value #MESH_W}×{@value #MESH_H} 网格，按区域给每个顶点加位移，
 * 配合 {@code Canvas.drawBitmapMesh} 让「一张死图」动起来。
 *
 * <p>模型（骨表 / 权重场 / 幅度上限）的唯一事实来源是 {@code tools/rig_math.py}，
 * 常量由 {@code tools/gen_rig_java.py} 生成到 {@link RigModel}，本类是那套公式的 Java 翻译。
 * 两边一致性由 {@code tools/verify_rig.py} 与 {@code test/RigGoldenTest.java} 双重保证。
 *
 * <p>位移分两层：
 * <ul>
 *   <li><b>结构层</b>（头 / 发丝 / 尾鳍 / 手臂）：{@code Σwᵢ·Δᵢ / max(1, Σwᵢ)}，权重场重叠处自动过渡；</li>
 *   <li><b>细节层</b>（呼吸 / 眨眼 / 手掌平移）：在归一化**之后**叠加，保证满幅不被摊薄。</li>
 * </ul>
 *
 * <p>动画层在这里：待机是几条互不通约的正弦（呼吸、重心漂移、风阵），
 * 发丝与尾鳍再挂一个二阶跟随（弹簧-阻尼），因此转头时头发会「晚一拍」甩过去 —— 动作不像贴纸的关键。
 * 所有驱动每帧按 {@link RigModel#DRIVER_LIMIT} 裁剪，动画永远不会越出已验证的幅度。
 */
public final class PartRig {

    /** 网格段数与顶点数。 */
    public static final int MW = RigModel.MESH_W, MH = RigModel.MESH_H;

    // ---- 权重探针下标（自检用，顺序固定） ----
    public static final int W_HEAD = 0, W_HAIR_L = 1, W_HAIR_R = 2, W_HAIR_F = 3, W_TAIL = 4,
            W_ARM_L = 5, W_ARM_R = 6, W_TORSO = 7, W_EYE_L = 8, W_EYE_R = 9,
            W_HAND_L = 10, W_HAND_R = 11, W_COUNT = 12;

    // ---- 动画常量（时间/节奏，不属于几何模型，故留在本类） ----
    /** 眨眼：间隔下限、随机增量、单次闭合时长、连眨间隔。 */
    private static final long BLINK_MIN_MS = 2200L, BLINK_JITTER_MS = 3800L,
            BLINK_MS = 175L, BLINK_CHAIN_MS = 95L;
    /** 连眨（眨两下）的概率。 */
    private static final float BLINK_DOUBLE_P = 0.22f;
    /** 发丝/尾鳍跟随的刚度与阻尼：约 0.45s 收敛，转头的甩动就来自这里。 */
    private static final float SPRING_STIFF = 46f, SPRING_DAMP = 12f;
    /** 发丝被头带动的牵连系数（取负号 = 头发向头的反方向甩）。 */
    private static final float COUP_H_L = -0.65f, COUP_H_R = -0.55f, COUP_H_F = -0.45f;
    private static final float COUP_DX = -0.30f, COUP_DX_F = -0.20f;

    /**
     * 动画幅度写法：<b>一切幅度都取「该驱动自身上限的比例」</b>，不再写死角度/像素。
     *
     * <p>上限由 {@code tools/fit_amps.py} 标定（不产生折痕的最大幅度），标定一改值，
     * 写死的角度就悄悄变成「看不见」。v1.6 就是这么坏的：待机甩尾只有 2.2°/9.4° = 23%、
     * 挥手平移 1.2px/10.5px = 11%，贴上再被视图缩到 0.3 倍，屏幕上只剩 1px 级抖动，
     * 于是被反馈成「尾巴不晃、头发不飘、动作生硬」。
     * 现在比例写在调用处，标定改值再也不会把动作改小。
     */
    private static float lim(int d, float frac) { return DRIVER_LIMIT[d] * frac; }

    private final float[] drv = new float[DRIVER_COUNT];
    private final float[] add = new float[DRIVER_COUNT];
    private final float[] verts = new float[(MW + 1) * (MH + 1) * 2];
    private final float[] ui = new float[MW + 1];
    private final float[] vj = new float[MH + 1];
    private final float[] tmp = new float[2];
    private final float[] w = new float[W_COUNT];

    /** 发丝/尾鳍的跟随状态：0 hair_l、1 hair_r、2 hair_f、3 tail。 */
    private final float[] sAng = new float[4];
    private final float[] sVel = new float[4];
    private boolean sInit;

    /**
     * 确定性伪随机（xorshift32）替代 java.util.Random：眨眼间隔是动画里唯一的随机源，
     * 用它固定种子之后「同一段时间轴 → 同一串动画」，{@code test/rig_anim_samples.txt}
     * 才能当黄金表用（否则每次跑都变，diff 全是噪声，也复现不出动作问题）。
     */
    private int rndState = 0x2F6E2B1;
    private long blinkAt = -1L;
    private int blinkChain = 0;
    private float blinkVal;

    private float rndNext() {
        int x = rndState;
        x ^= x << 13;
        x ^= x >>> 17;
        x ^= x << 5;
        rndState = x;
        return (x >>> 8) / (float) (1 << 24);
    }

    private long lastMs = -1L;
    private float ddx, ddy;
    private boolean neutral = true;

    public PartRig() {
        for (int i = 0; i <= MW; i++) ui[i] = i / (float) MW;
        for (int j = 0; j <= MH; j++) vj[j] = j / (float) MH;
    }

    // ==================== 动画层 ====================

    /**
     * 推进到 now 时刻，算出这一帧的驱动量。
     *
     * @param action   正在播放的动作（{@link PetAction#IDLE} 表示只有待机驱动）
     * @param progress 动作进度 0..1；负数表示没有动作
     * @param animated false 时完全静止（省电），并清零跟随状态
     */
    public void update(long now, PetAction action, float progress, boolean animated) {
        if (!animated) {
            for (int i = 0; i < DRIVER_COUNT; i++) drv[i] = 0f;
            sInit = false;
            blinkVal = 0f;
            lastMs = now;
            neutral = true;
            return;
        }
        float dt = 0f;
        if (lastMs > 0L) dt = Math.min(0.05f, (now - lastMs) / 1000f);
        lastMs = now;
        float t = now / 1000f;

        for (int i = 0; i < DRIVER_COUNT; i++) add[i] = 0f;

        // 待机：互不通约的正弦，避免整只角色「一起呼吸」的机械感。
        // 幅度一律按 lim(驱动, 上限比例) 写，见 lim() 的注释。
        drv[D_WIND] = 0.42f + 0.34f * osc(t, 11.3f, 0.70f) + 0.16f * osc(t, 3.9f, 0f);
        drv[D_BREATH] = lim(D_BREATH, 0.50f) * osc(t, 3.7f, 0f);
        drv[D_HEAD_DY] = lim(D_HEAD_DY, 0.35f) * osc(t, 3.7f, 0.60f);
        drv[D_HEAD_DX] = lim(D_HEAD_DX, 0.40f) * osc(t, 9.4f, 1.10f);
        drv[D_HEAD_ROT] = lim(D_HEAD_ROT, 0.45f) * osc(t, 8.1f, 0.30f);
        drv[D_ARM_L] = lim(D_ARM_L, 0.35f) * osc(t, 6.2f, 0.90f);
        drv[D_ARM_R] = lim(D_ARM_R, 0.40f) * osc(t, 5.4f, 0f);
        drv[D_HAND_L] = lim(D_HAND_L, 0.30f) * osc(t, 7.3f, 0.40f);
        drv[D_HAND_R] = lim(D_HAND_R, 0.30f) * osc(t, 6.9f, 1.70f);

        if (action != null && action != PetAction.IDLE && progress >= 0f) mood(action, progress);

        for (int i = 0; i < DRIVER_COUNT; i++) drv[i] += add[i];

        // 发丝/尾鳍：目标角 = 自身摆动 + 被头牵连 + 动作贡献，交给二阶跟随
        float hRot = drv[D_HEAD_ROT], hDx = drv[D_HEAD_DX];
        float tHairL = lim(D_HAIR_L, 0.55f) * osc(t, 4.9f, 0f) + COUP_H_L * hRot + COUP_DX * hDx
                + add[D_HAIR_L];
        float tHairR = lim(D_HAIR_R, 0.50f) * osc(t, 5.6f, 1.3f) + COUP_H_R * hRot + COUP_DX * hDx
                + add[D_HAIR_R];
        float tHairF = lim(D_HAIR_F, 0.45f) * osc(t, 4.2f, 2.1f) + COUP_H_F * hRot + COUP_DX_F * hDx
                + add[D_HAIR_F];
        float tTail = lim(D_TAIL, 0.60f) * osc(t, 6.7f, 0.5f) + add[D_TAIL];
        if (!sInit) {
            sAng[0] = tHairL;
            sAng[1] = tHairR;
            sAng[2] = tHairF;
            sAng[3] = tTail;
            sVel[0] = sVel[1] = sVel[2] = sVel[3] = 0f;
            sInit = true;
        } else if (dt > 0f) {
            follow(0, tHairL, dt);
            follow(1, tHairR, dt);
            follow(2, tHairF, dt);
            follow(3, tTail, dt);
        }
        drv[D_HAIR_L] = sAng[0];
        drv[D_HAIR_R] = sAng[1];
        drv[D_HAIR_F] = sAng[2];
        drv[D_TAIL] = sAng[3];

        // 眨眼（打瞌睡时由动作压住半闭，取较大者）
        blinkTick(now);
        if (add[D_BLINK] > blinkVal) blinkVal = add[D_BLINK];
        drv[D_BLINK] = blinkVal;

        // 裁剪：动画永远不会越出 tools/fit_amps.py 标定过的幅度
        for (int i = 0; i < DRIVER_COUNT; i++) {
            float lim = DRIVER_LIMIT[i];
            if (drv[i] > lim) drv[i] = lim;
            else if (drv[i] < -lim) drv[i] = -lim;
        }
    }

    /** 单根骨跟随目标角（半隐式欧拉，dt 已限幅）。 */
    private void follow(int i, float target, float dt) {
        float acc = (target - sAng[i]) * SPRING_STIFF - sVel[i] * SPRING_DAMP;
        sVel[i] += acc * dt;
        sAng[i] += sVel[i] * dt;
    }

    /** 动作层：把动作进度映射成驱动增量（全部写进 add[]，之后统一裁剪）。 */
    private void mood(PetAction a, float p) {
        float e = (float) Math.sin(Math.PI * clamp01(p));                  // 0→1→0 包络
        float hold = (float) Math.min(1.0, Math.min(p / 0.30f, (1f - p) / 0.30f));
        hold = clamp01(hold);
        switch (a) {
            case FLOAT:
                add[D_TAIL] += lim(D_TAIL, 0.35f) * e * sw(p, 1.0f, 0.25f);
                add[D_HAIR_L] += lim(D_HAIR_L, 0.32f) * e;
                add[D_HAIR_R] -= lim(D_HAIR_R, 0.30f) * e;
                add[D_HAIR_F] += lim(D_HAIR_F, 0.28f) * e;
                add[D_HEAD_DY] += lim(D_HEAD_DY, 0.20f) * e * sw(p, 1.0f, 0.25f);
                break;
            case BOUNCE: {
                // 落地后发丝/尾巴被惯性甩一下，衰减振荡
                float land = p < 0.5f ? 0f : (float) Math.exp(-6f * (p - 0.5f) / 0.5f);
                add[D_BREATH] -= lim(D_BREATH, 0.55f) * land;
                add[D_HAIR_L] += lim(D_HAIR_L, 0.65f) * land;
                add[D_HAIR_R] -= lim(D_HAIR_R, 0.60f) * land;
                add[D_HAIR_F] += lim(D_HAIR_F, 0.50f) * land;
                add[D_TAIL] += lim(D_TAIL, 0.45f) * land * sw(p, 2.5f, 0f);
                add[D_ARM_L] += lim(D_ARM_L, 0.35f) * land;
                add[D_ARM_R] -= lim(D_ARM_R, 0.35f) * land;
                break;
            }
            case SWAY:
                add[D_HEAD_ROT] += lim(D_HEAD_ROT, 0.75f) * e * sw(p, 1.5f, 0f);
                add[D_HEAD_DX] += lim(D_HEAD_DX, 0.55f) * e;
                add[D_TAIL] += lim(D_TAIL, 0.25f) * e * sw(p, 1.0f, 0.30f);
                break;
            case LEAN:
                add[D_HEAD_ROT] += lim(D_HEAD_ROT, 0.90f) * hold;
                add[D_HEAD_DX] += lim(D_HEAD_DX, 0.85f) * hold;
                add[D_HAIR_L] += lim(D_HAIR_L, 0.55f) * hold;
                add[D_HAIR_F] += lim(D_HAIR_F, 0.32f) * hold;
                add[D_HAND_L] += lim(D_HAND_L, 0.30f) * hold;
                break;
            case SHAKE:
                add[D_HEAD_DX] += lim(D_HEAD_DX, 1.00f) * e * sw(p, 2.2f, 0f);
                add[D_HEAD_ROT] += lim(D_HEAD_ROT, 0.35f) * e * sw(p, 2.2f, 0.25f);
                break;
            case NOD:
                add[D_HEAD_DY] += lim(D_HEAD_DY, 0.90f) * e * sw(p, 1.0f, 0f);
                add[D_HAIR_F] += lim(D_HAIR_F, 0.50f) * e;
                break;
            case SPIN:
                add[D_HAIR_L] += lim(D_HAIR_L, 0.65f) * e;
                add[D_HAIR_R] -= lim(D_HAIR_R, 0.65f) * e;
                add[D_HAIR_F] += lim(D_HAIR_F, 0.38f) * e;
                add[D_TAIL] += lim(D_TAIL, 0.50f) * e;
                add[D_ARM_L] += lim(D_ARM_L, 0.50f) * e;
                add[D_ARM_R] -= lim(D_ARM_R, 0.50f) * e;
                break;
            case STRETCH:
                add[D_BREATH] += lim(D_BREATH, 0.60f) * e;
                add[D_HEAD_DY] -= lim(D_HEAD_DY, 0.30f) * e;
                add[D_ARM_L] += lim(D_ARM_L, 0.70f) * e;
                add[D_ARM_R] -= lim(D_ARM_R, 0.70f) * e;
                add[D_HAIR_F] -= lim(D_HAIR_F, 0.45f) * e;
                break;
            case DUCK:
                add[D_BREATH] -= lim(D_BREATH, 0.45f) * e;
                add[D_HEAD_DY] += lim(D_HEAD_DY, 0.55f) * e;
                add[D_HAIR_F] += lim(D_HAIR_F, 0.45f) * e;
                break;
            case POP: {
                float fast = (float) Math.sqrt(clamp01(e));   // 起手快 = 吓一跳
                add[D_HEAD_DY] -= lim(D_HEAD_DY, 0.45f) * fast;
                add[D_HAIR_L] += lim(D_HAIR_L, 0.55f) * fast;
                add[D_HAIR_R] -= lim(D_HAIR_R, 0.50f) * fast;
                add[D_HAIR_F] += lim(D_HAIR_F, 0.55f) * fast;
                add[D_TAIL] += lim(D_TAIL, 0.40f) * fast;
                add[D_ARM_L] += lim(D_ARM_L, 0.45f) * fast;
                add[D_ARM_R] -= lim(D_ARM_R, 0.45f) * fast;
                add[D_BREATH] += lim(D_BREATH, 0.35f) * fast;
                break;
            }
            case SLEEPY:
                add[D_BREATH] += lim(D_BREATH, 0.55f) * e * sw(p, 1.5f, 0.25f);
                add[D_HEAD_ROT] += lim(D_HEAD_ROT, 0.60f) * e;
                add[D_HEAD_DY] += lim(D_HEAD_DY, 0.40f) * e;
                add[D_HAIR_L] += lim(D_HAIR_L, 0.28f) * e;
                add[D_BLINK] += 0.45f * e;        // 半眯着
                break;
            case WAG:
                add[D_TAIL] += lim(D_TAIL, 0.80f) * e * sw(p, 2.2f, 0.10f);
                add[D_HAND_L] += lim(D_HAND_L, 0.45f) * e * sw(p, 2.2f, 0f);
                add[D_HAIR_L] += lim(D_HAIR_L, 0.28f) * e;
                add[D_HEAD_ROT] += lim(D_HEAD_ROT, 0.30f) * e;
                break;
            case WAVE:
                add[D_HAND_R] += lim(D_HAND_R, 1.00f) * e * sw(p, 3.2f, 0f);  // 挥手主体：手掌来回平移
                add[D_ARM_R] -= lim(D_ARM_R, 0.80f) * hold;                   // 抬臂
                add[D_HEAD_DX] += lim(D_HEAD_DX, 0.35f) * hold;
                add[D_HEAD_ROT] -= lim(D_HEAD_ROT, 0.28f) * hold;
                add[D_HAIR_R] += lim(D_HAIR_R, 0.40f) * hold;
                break;
            default:
                break;
        }
    }

    /** 眨眼调度：随机间隔、偶尔连眨两下。 */
    private void blinkTick(long now) {
        if (blinkAt < 0L) {
            blinkAt = now + BLINK_MIN_MS + (long) (rndNext() * BLINK_JITTER_MS);
        }
        long dt = now - blinkAt;
        if (dt >= BLINK_MS) {
            if (dt < BLINK_MS + BLINK_CHAIN_MS && blinkChain > 0) {
                blinkChain--;
                blinkAt = now + BLINK_CHAIN_MS;
            } else if (dt >= BLINK_MS + BLINK_CHAIN_MS) {
                blinkChain = rndNext() < BLINK_DOUBLE_P ? 1 : 0;
                blinkAt = now + BLINK_MIN_MS + (long) (rndNext() * BLINK_JITTER_MS);
            }
            blinkVal = 0f;
            return;
        }
        if (dt >= 0L) blinkVal = (float) Math.sin(Math.PI * dt / (double) BLINK_MS);
    }

    private static float osc(float t, float period, float phase) {
        return (float) Math.sin(2.0 * Math.PI * (t / period) + phase);
    }

    /** 动作内的往复摆动：cycles 次正弦，phase 为相位（0..1）。 */
    private static float sw(float p, float cycles, float phase) {
        return (float) Math.sin(2.0 * Math.PI * (p * cycles + phase));
    }

    // ==================== 位移场 ====================

    /** 计算 (x,y) 处的位移，结果放在 {@link #ddx}/{@link #ddy}。 */
    private void disp(float x, float y) {
        float ax = 0f, ay = 0f, wsum = 0f;
        float wt = wHead(x, y);
        if (wt > 0f) {
            float rad = (float) Math.toRadians(drv[D_HEAD_ROT]);
            // cos-1：返回的是「相对不转时的位移」，不是旋转后的坐标（见 RigModel 注释）
            float ca = (float) Math.cos(rad) - 1f, sa = (float) Math.sin(rad);
            float rx = x - HEAD_PX, ry = y - HEAD_PY;
            ax += wt * (rx * ca - ry * sa + drv[D_HEAD_DX]);
            ay += wt * (rx * sa + ry * ca + drv[D_HEAD_DY]);
            wsum += wt;
        }

        // 发丝 / 尾鳍：驱动角 + wind×骨风增益
        wt = strand(x, y, drv[D_HAIR_L] + drv[D_WIND] * HAIR_L_WIND, HAIR_L_PX, HAIR_L_PY,
                HAIR_L_X0, HAIR_L_Y0, HAIR_L_X1, HAIR_L_Y1, HAIR_L_R0, HAIR_L_R1, HAIR_L_K, HAIR_L_TS);
        if (wt > 0f) { ax += wt * tmp[0]; ay += wt * tmp[1]; wsum += wt; }
        wt = strand(x, y, drv[D_HAIR_R] + drv[D_WIND] * HAIR_R_WIND, HAIR_R_PX, HAIR_R_PY,
                HAIR_R_X0, HAIR_R_Y0, HAIR_R_X1, HAIR_R_Y1, HAIR_R_R0, HAIR_R_R1, HAIR_R_K, HAIR_R_TS);
        if (wt > 0f) { ax += wt * tmp[0]; ay += wt * tmp[1]; wsum += wt; }
        wt = strand(x, y, drv[D_HAIR_F] + drv[D_WIND] * HAIR_F_WIND, HAIR_F_PX, HAIR_F_PY,
                HAIR_F_X0, HAIR_F_Y0, HAIR_F_X1, HAIR_F_Y1, HAIR_F_R0, HAIR_F_R1, HAIR_F_K, HAIR_F_TS);
        if (wt > 0f) { ax += wt * tmp[0]; ay += wt * tmp[1]; wsum += wt; }
        wt = strand(x, y, drv[D_TAIL] + drv[D_WIND] * TAIL_WIND, TAIL_PX, TAIL_PY,
                TAIL_X0, TAIL_Y0, TAIL_X1, TAIL_Y1, TAIL_R0, TAIL_R1, TAIL_K, TAIL_TS);
        if (wt > 0f) { ax += wt * tmp[0]; ay += wt * tmp[1]; wsum += wt; }

        // 双臂（不随风）
        wt = strand(x, y, drv[D_ARM_L], ARM_L_PX, ARM_L_PY, ARM_L_X0, ARM_L_Y0, ARM_L_X1, ARM_L_Y1,
                ARM_L_R0, ARM_L_R1, ARM_L_K, ARM_L_TS);
        if (wt > 0f) { ax += wt * tmp[0]; ay += wt * tmp[1]; wsum += wt; }
        wt = strand(x, y, drv[D_ARM_R], ARM_R_PX, ARM_R_PY, ARM_R_X0, ARM_R_Y0, ARM_R_X1, ARM_R_Y1,
                ARM_R_R0, ARM_R_R1, ARM_R_K, ARM_R_TS);
        if (wt > 0f) { ax += wt * tmp[0]; ay += wt * tmp[1]; wsum += wt; }

        float k = wsum > 1f ? wsum : 1f;
        ax /= k;
        ay /= k;

        // 细节层 1：呼吸
        if (drv[D_BREATH] != 0f) {
            wt = wTorso(x, y);
            if (wt > 0f) {
                float sy = drv[D_BREATH] * TORSO_AMP;
                float sx = -drv[D_BREATH] * TORSO_AMP * TORSO_SQUEEZE;
                ax += wt * (x - TORSO_PX) * sx;
                ay += wt * (y - TORSO_PY) * sy;
            }
        }
        // 细节层 2：眨眼
        if (drv[D_BLINK] > 0f) {
            wt = wEye(x, y, EYE_L_CX, EYE_L_CY, EYE_L_RX, EYE_L_RY, EYE_L_SOFT);
            if (wt > 0f) ay += wt * (EYE_L_CLOSE_Y - y) * EYE_L_AMP * drv[D_BLINK];
            wt = wEye(x, y, EYE_R_CX, EYE_R_CY, EYE_R_RX, EYE_R_RY, EYE_R_SOFT);
            if (wt > 0f) ay += wt * (EYE_R_CLOSE_Y - y) * EYE_R_AMP * drv[D_BLINK];
        }
        // 细节层 3：手掌平移
        if (drv[D_HAND_L] != 0f) {
            wt = wHand(x, y, HAND_L_CX, HAND_L_CY, HAND_L_R, HAND_L_K);
            if (wt > 0f) {
                ax += wt * drv[D_HAND_L] * HAND_L_DIRX;
                ay += wt * drv[D_HAND_L] * HAND_L_DIRY;
            }
        }
        if (drv[D_HAND_R] != 0f) {
            wt = wHand(x, y, HAND_R_CX, HAND_R_CY, HAND_R_R, HAND_R_K);
            if (wt > 0f) {
                ax += wt * drv[D_HAND_R] * HAND_R_DIRX;
                ay += wt * drv[D_HAND_R] * HAND_R_DIRY;
            }
        }
        ddx = ax;
        ddy = ay;
    }

    /** 骨段绕支点旋转对 (x,y) 的贡献：位移写进 tmp，返回权重。
     *
     * <p>角度为 0 时也会返回权重（位移为 0）——归一化分母必须与 Python 侧同源：
     * 不动的骨也占一份权重，否则 k=max(1,Σw) 会在角度过零时跳变。
     */
    private float strand(float x, float y, float angDeg, float px, float py,
                         float x0, float y0, float x1, float y1,
                         float r0, float r1, float kk, float ts) {
        float dist, tt = 0f;
        float vx = x1 - x0, vy = y1 - y0;
        float l2 = vx * vx + vy * vy;
        if (l2 > 0f) tt = clamp01(((x - x0) * vx + (y - y0) * vy) / l2);
        float cxp = x0 + vx * tt, cyp = y0 + vy * tt;
        dist = (float) Math.hypot(x - cxp, y - cyp);
        float rr = r0 + (r1 - r0) * tt;
        float wt = softstep(dist, rr, rr * (1f + kk)) * smooth(tt / ts);
        if (wt <= 0f) return 0f;
        float rad = (float) Math.toRadians(angDeg);
        float ca = (float) Math.cos(rad) - 1f, sa = (float) Math.sin(rad);
        float rx = x - px, ry = y - py;
        tmp[0] = rx * ca - ry * sa;
        tmp[1] = rx * sa + ry * ca;
        return wt;
    }

    // ---- 权重场 ----

    static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    static float smooth(float v) {
        v = clamp01(v);
        return v * v * (3f - 2f * v);
    }

    static float softstep(float d, float inner, float outer) {
        if (outer <= inner) return d <= inner ? 1f : 0f;
        return smooth((outer - d) / (outer - inner));
    }

    /** 头部权重：颈线以上为 1，向下 {@link RigModel#HEAD_WIN_FADE} 像素内归零。 */
    static float wHead(float x, float y) {
        float wy = smooth((HEAD_WIN_Y - y) / HEAD_WIN_FADE);
        if (wy <= 0f) return 0f;
        return wy * softstep(Math.abs(x - HEAD_WIN_CX), HEAD_WIN_FULL, HEAD_WIN_ZERO);
    }

    /** 躯干权重：只盖住身体，横向收窄避免把双手拉进呼吸缩放。 */
    static float wTorso(float x, float y) {
        float wy = smooth((y - TORSO_FADE_Y0) / TORSO_FADE) * smooth((TORSO_FADE_Y1 - y) / TORSO_FADE);
        if (wy <= 0f) return 0f;
        return wy * softstep(Math.abs(x - TORSO_WIN_CX), TORSO_WIN_FULL, TORSO_WIN_ZERO);
    }

    /** 眼睛权重：椭圆内为 1，向外 soft 像素平滑归零。 */
    static float wEye(float x, float y, float cx, float cy, float rx, float ry, float soft) {
        float dx = Math.max(0f, Math.abs(x - cx) - rx);
        float dy = Math.max(0f, Math.abs(y - cy) - ry);
        return softstep((float) Math.hypot(dx, dy), 0f, soft);
    }

    /** 手掌权重：径向软罩。 */
    static float wHand(float x, float y, float cx, float cy, float r, float k) {
        return softstep((float) Math.hypot(x - cx, y - cy), r, r * (1f + k));
    }

    /**
     * 12 个权重场的当前值（自检用，顺序见 W_* 常量）。
     *
     * <p>返回的是新数组而不是内部缓存：内部缓存会被下一次调用覆写，
     * 调用方一旦交叉取值（{@code a = weightsAt(p); b = weightsAt(q); 用 a[...]}）
     * 就会静默读到 q 的值。这类错误只在测试里出现过，但很难查，所以 API 层面直接断开别名。
     */
    public float[] weightsAt(float x, float y) {
        fillWeights(x, y);
        float[] out = new float[W_COUNT];
        System.arraycopy(w, 0, out, 0, W_COUNT);
        return out;
    }

    /** 把 12 个权重场写进内部缓存（{@link #weightsAt} 的旧契约，勿外泄）。 */
    private void fillWeights(float x, float y) {
        w[W_HEAD] = wHead(x, y);
        w[W_HAIR_L] = strandW(x, y, HAIR_L_X0, HAIR_L_Y0, HAIR_L_X1, HAIR_L_Y1, HAIR_L_R0, HAIR_L_R1, HAIR_L_K, HAIR_L_TS);
        w[W_HAIR_R] = strandW(x, y, HAIR_R_X0, HAIR_R_Y0, HAIR_R_X1, HAIR_R_Y1, HAIR_R_R0, HAIR_R_R1, HAIR_R_K, HAIR_R_TS);
        w[W_HAIR_F] = strandW(x, y, HAIR_F_X0, HAIR_F_Y0, HAIR_F_X1, HAIR_F_Y1, HAIR_F_R0, HAIR_F_R1, HAIR_F_K, HAIR_F_TS);
        w[W_TAIL] = strandW(x, y, TAIL_X0, TAIL_Y0, TAIL_X1, TAIL_Y1, TAIL_R0, TAIL_R1, TAIL_K, TAIL_TS);
        w[W_ARM_L] = strandW(x, y, ARM_L_X0, ARM_L_Y0, ARM_L_X1, ARM_L_Y1, ARM_L_R0, ARM_L_R1, ARM_L_K, ARM_L_TS);
        w[W_ARM_R] = strandW(x, y, ARM_R_X0, ARM_R_Y0, ARM_R_X1, ARM_R_Y1, ARM_R_R0, ARM_R_R1, ARM_R_K, ARM_R_TS);
        w[W_TORSO] = wTorso(x, y);
        w[W_EYE_L] = wEye(x, y, EYE_L_CX, EYE_L_CY, EYE_L_RX, EYE_L_RY, EYE_L_SOFT);
        w[W_EYE_R] = wEye(x, y, EYE_R_CX, EYE_R_CY, EYE_R_RX, EYE_R_RY, EYE_R_SOFT);
        w[W_HAND_L] = wHand(x, y, HAND_L_CX, HAND_L_CY, HAND_L_R, HAND_L_K);
        w[W_HAND_R] = wHand(x, y, HAND_R_CX, HAND_R_CY, HAND_R_R, HAND_R_K);
    }

    /** 纯权重（不写 tmp），供探针使用。 */
    static float strandW(float x, float y, float x0, float y0, float x1, float y1,
                         float r0, float r1, float kk, float ts) {
        float vx = x1 - x0, vy = y1 - y0;
        float l2 = vx * vx + vy * vy;
        float tt = l2 > 0f ? clamp01(((x - x0) * vx + (y - y0) * vy) / l2) : 0f;
        float cxp = x0 + vx * tt, cyp = y0 + vy * tt;
        float rr = r0 + (r1 - r0) * tt;
        float d = (float) Math.hypot(x - cxp, y - cyp);
        return softstep(d, rr, rr * (1f + kk)) * smooth(tt / ts);
    }

    // ==================== 网格与测试接口 ====================

    /** 生成网格顶点（画布坐标，未含整体位移/缩放，由调用方用 canvas 变换处理）。 */
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
    public boolean isNeutral() {
        return neutral;
    }

    /** 顶点数组（长度 = (MW+1)*(MH+1)*2）。 */
    public float[] vertices() {
        return verts;
    }

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
        return drv[D_BLINK];
    }

    /** 当前驱动向量（自检用；顺序见 {@link RigModel#DRIVER_NAMES}）。 */
    public float[] drivers() {
        return drv;
    }

    /** 自检用：直接设定驱动向量并跳过动画层。 */
    public void setDrivers(float[] src) {
        for (int i = 0; i < DRIVER_COUNT && i < src.length; i++) drv[i] = src[i];
    }

    /** 自检用：把下一次眨眼强制安排到 now（绕开随机间隔），便于验证眨眼时序。 */
    public void forceBlinkAt(long now) {
        blinkAt = now;
        blinkChain = 0;
        blinkVal = 0f;
    }

    public static int meshWidth() {
        return MW;
    }

    public static int meshHeight() {
        return MH;
    }

    /** 驱动个数（自检/黄金表用，顺序见 {@link RigModel#DRIVER_NAMES}）。 */
    public static int driverCount() {
        return DRIVER_COUNT;
    }

    /** 第 i 个驱动的裁剪上限（绝对值），供单测构造「拉到模型验证过的极值」。 */
    public static float driverLimit(int i) {
        return DRIVER_LIMIT[i];
    }

    /** 驱动名（下标即驱动序号），自检与黄金表用；返回副本，防止被调用方改坏。 */
    public static String[] driverNames() {
        return DRIVER_NAMES.clone();
    }

    /**
     * 脸芯椭圆 {cx, cy, rx, ry}，与 {@code tools/rig_math.py:face_core()} 同一份定义
     * （常量由 RigModel 从 rig_math.FACE 生成）。单测用它判「脸有没有被发丝/尾巴拖走」，
     * 不再自己画方框 —— 坐标必须跟着模型走，否则改场之后判据会静默失真。
     */
    public static float[] faceCore() {
        return new float[] {FACE_CX, FACE_CY, FACE_RX * FACE_SHRINK, FACE_RY * FACE_SHRINK};
    }

    /** 点是否在脸芯椭圆内，等价于 Python 的 {@code rig_math.in_face(x, y)}。 */
    public static boolean inFace(float x, float y) {
        float dx = (x - FACE_CX) / (FACE_RX * FACE_SHRINK);
        float dy = (y - FACE_CY) / (FACE_RY * FACE_SHRINK);
        return dx * dx + dy * dy <= 1f;
    }

    /** 按名字取驱动下标，找不到返回 -1。 */
    public static int driverIndex(String name) {
        for (int i = 0; i < DRIVER_COUNT; i++) {
            if (DRIVER_NAMES[i].equals(name)) return i;
        }
        return -1;
    }
}

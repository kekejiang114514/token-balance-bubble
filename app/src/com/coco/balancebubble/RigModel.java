package com.coco.balancebubble;

/**
 * 骨架模型常量。**本文件由 tools/gen_rig_java.py 从 tools/rig_math.py 生成，请勿手改。**
 *
 * 坐标一律是贴图像素（692×720）。骨表、权重场、幅度上限的唯一事实来源是
 * <code>tools/rig_math.py</code>；改模型请改那里，然后跑
 * <code>python3 tools/verify_rig.py</code> 验证、<code>python3 tools/gen_rig_java.py</code> 落盘。
 *
 * 幅度上限由 tools/fit_amps.py 二分标定（判据：网格无折叠、单元面积≥0.55、
 * 主伸缩∈[0.55,1.80]、剪切≤0.80、位移≤125px，眼区不计）。
 */
final class RigModel {
    private RigModel() {}

    // ---- 画布与网格 ----
    static final float RAW_W = 692.0000f, RAW_H = 720.0000f;
    static final int MESH_W = 24, MESH_H = 24;

    // ---- 头：绕颈点旋转（歪头）+ 摇头/点头平移 ----
    static final float HEAD_PX = 300.0000f, HEAD_PY = 442.0000f;
    static final float HEAD_AMP = 4.2000f;
    static final float HEAD_WIN_CX = 300.0000f, HEAD_WIN_FULL = 205.0000f, HEAD_WIN_ZERO = 335.0000f;
    static final float HEAD_WIN_Y = 392.0000f, HEAD_WIN_FADE = 150.0000f;

    // ---- 长发（左）：绕支点旋转，权重沿骨段向梢部递增 ----
    static final float HAIR_L_PX = 168.0000f, HAIR_L_PY = 408.0000f;
    static final float HAIR_L_AMP = 3.2000f;
    static final float HAIR_L_WIND = 1.7000f;   // 风对旋转角的附加增益（度）
    static final float HAIR_L_X0 = 168.0000f, HAIR_L_Y0 = 408.0000f, HAIR_L_X1 = 88.0000f, HAIR_L_Y1 = 652.0000f;
    static final float HAIR_L_R0 = 34.0000f, HAIR_L_R1 = 74.0000f;
    static final float HAIR_L_K = 1.5500f, HAIR_L_TS = 0.3000f;

    // ---- 长发（右）：绕支点旋转，权重沿骨段向梢部递增 ----
    static final float HAIR_R_PX = 498.0000f, HAIR_R_PY = 415.0000f;
    static final float HAIR_R_AMP = 2.8500f;
    static final float HAIR_R_WIND = 1.5500f;   // 风对旋转角的附加增益（度）
    static final float HAIR_R_X0 = 498.0000f, HAIR_R_Y0 = 415.0000f, HAIR_R_X1 = 545.0000f, HAIR_R_Y1 = 660.0000f;
    static final float HAIR_R_R0 = 34.0000f, HAIR_R_R1 = 74.0000f;
    static final float HAIR_R_K = 1.5500f, HAIR_R_TS = 0.3000f;

    // ---- 刘海：绕支点旋转，权重沿骨段向梢部递增 ----
    static final float HAIR_F_PX = 450.0000f, HAIR_F_PY = 330.0000f;
    static final float HAIR_F_AMP = 2.5000f;
    static final float HAIR_F_WIND = 1.0000f;   // 风对旋转角的附加增益（度）
    static final float HAIR_F_X0 = 450.0000f, HAIR_F_Y0 = 330.0000f, HAIR_F_X1 = 432.0000f, HAIR_F_Y1 = 452.0000f;
    static final float HAIR_F_R0 = 20.0000f, HAIR_F_R1 = 26.0000f;
    static final float HAIR_F_K = 1.3500f, HAIR_F_TS = 0.3500f;

    // ---- 尾鳍：绕支点旋转，权重沿骨段向梢部递增 ----
    static final float TAIL_PX = 588.0000f, TAIL_PY = 518.0000f;
    static final float TAIL_AMP = 7.4000f;
    static final float TAIL_WIND = 2.0000f;   // 风对旋转角的附加增益（度）
    static final float TAIL_X0 = 588.0000f, TAIL_Y0 = 518.0000f, TAIL_X1 = 692.0000f, TAIL_Y1 = 455.0000f;
    static final float TAIL_R0 = 30.0000f, TAIL_R1 = 48.0000f;
    static final float TAIL_K = 1.6000f, TAIL_TS = 0.3500f;

    // ---- 手臂（左）：绕支点旋转，权重沿骨段向梢部递增 ----
    static final float ARM_L_PX = 222.0000f, ARM_L_PY = 440.0000f;
    static final float ARM_L_AMP = 7.4000f;
    static final float ARM_L_X0 = 222.0000f, ARM_L_Y0 = 440.0000f, ARM_L_X1 = 214.0000f, ARM_L_Y1 = 500.0000f;
    static final float ARM_L_R0 = 20.0000f, ARM_L_R1 = 30.0000f;
    static final float ARM_L_K = 2.8000f, ARM_L_TS = 0.5000f;

    // ---- 手臂（右）：绕支点旋转，权重沿骨段向梢部递增 ----
    static final float ARM_R_PX = 372.0000f, ARM_R_PY = 440.0000f;
    static final float ARM_R_AMP = 7.4500f;
    static final float ARM_R_X0 = 372.0000f, ARM_R_Y0 = 440.0000f, ARM_R_X1 = 378.0000f, ARM_R_Y1 = 500.0000f;
    static final float ARM_R_R0 = 20.0000f, ARM_R_R1 = 30.0000f;
    static final float ARM_R_K = 2.8000f, ARM_R_TS = 0.5000f;

    // ---- 手掌（左）：挥手主体是刚体平移，不是旋转 ----
    static final float HAND_L_CX = 214.0000f, HAND_L_CY = 496.0000f;
    static final float HAND_L_R = 20.0000f, HAND_L_K = 3.3000f;
    static final float HAND_L_DIRX = 1.0000f, HAND_L_DIRY = -0.3500f;
    static final float HAND_L_AMP = 10.5000f;

    // ---- 手掌（右）：挥手主体是刚体平移，不是旋转 ----
    static final float HAND_R_CX = 378.0000f, HAND_R_CY = 493.0000f;
    static final float HAND_R_R = 20.0000f, HAND_R_K = 3.3000f;
    static final float HAND_R_DIRX = 1.0000f, HAND_R_DIRY = -0.3500f;
    static final float HAND_R_AMP = 10.5000f;

    // ---- 躯干：呼吸（纵向缩放，横向压 squeeze 倍） ----
    static final float TORSO_PX = 300.0000f, TORSO_PY = 620.0000f;
    static final float TORSO_AMP = 0.0300f, TORSO_SQUEEZE = 0.4500f;
    static final float TORSO_WIN_CX = 300.0000f, TORSO_WIN_FULL = 80.0000f, TORSO_WIN_ZERO = 140.0000f;
    static final float TORSO_FADE_Y0 = 415.0000f, TORSO_FADE_Y1 = 690.0000f, TORSO_FADE = 70.0000f;

    // ---- 左眼：眨眼时整块压到睑线 ----
    static final float EYE_L_CX = 227.0000f, EYE_L_CY = 348.0000f;
    static final float EYE_L_RX = 26.0000f, EYE_L_RY = 32.0000f, EYE_L_SOFT = 26.0000f;
    static final float EYE_L_CLOSE_Y = 373.0000f, EYE_L_AMP = 0.8500f;

    // ---- 右眼：眨眼时整块压到睑线 ----
    static final float EYE_R_CX = 364.0000f, EYE_R_CY = 338.0000f;
    static final float EYE_R_RX = 28.0000f, EYE_R_RY = 33.0000f, EYE_R_SOFT = 26.0000f;
    static final float EYE_R_CLOSE_Y = 364.0000f, EYE_R_AMP = 0.8500f;

    // ---- 脸芯保护区（椭圆 cx,cy,rx,ry 按 shrink 收缩）----
    // 发丝/尾巴/手/手臂都不许把这块地的像素拖走。定义来自 rig_math.FACE，
    // 不是手画的方框：旧版 Java 单测自己写了 x∈[240,430] y∈[340,560] 的矩形，
    // 场改过之后那个矩形一半落在脸外，于是「脸被发丝带走 N 处」全是假失败。
    static final float FACE_CX = 295.5000f, FACE_CY = 339.5000f;
    static final float FACE_RX = 138.7000f, FACE_RY = 104.7000f, FACE_SHRINK = 0.7500f;

    // ---- 驱动上限（动画层每帧按这个裁剪，保证永远落在已验证的幅度内） ----
    // 发丝/尾鳍是「驱动角 + wind×骨风增益」，所以驱动本身只能用到骨幅度（amp），
    // 上限值 = amp + wind×MAX_WIND 由 verify_rig 第 A 节核对。
    static final float MAX_HEAD_ROT = 4.2000f;
    static final float MAX_HEAD_DX = 6.0000f;
    static final float MAX_HEAD_DY = 6.0000f;
    static final float MAX_HAIR_L = 3.2000f;
    static final float MAX_HAIR_R = 2.8500f;
    static final float MAX_HAIR_F = 2.5000f;
    static final float MAX_TAIL = 7.4000f;
    static final float MAX_ARM_L = 7.4000f;
    static final float MAX_ARM_R = 7.4500f;
    static final float MAX_HAND_L = 10.5000f;
    static final float MAX_HAND_R = 10.5000f;
    static final float MAX_BREATH = 1.0000f;
    static final float MAX_BLINK = 1.0000f;
    static final float MAX_WIND = 1.0000f;

    // ---- 驱动下标（驱动数组的顺序，动画层与自检共用） ----
    static final int D_HEAD_ROT = 0, D_HEAD_DX = 1, D_HEAD_DY = 2;
    static final int D_HAIR_L = 3, D_HAIR_R = 4, D_HAIR_F = 5, D_TAIL = 6;
    static final int D_ARM_L = 7, D_ARM_R = 8;
    static final int D_HAND_L = 9, D_HAND_R = 10;
    static final int D_BREATH = 11, D_BLINK = 12, D_WIND = 13;
    static final int DRIVER_COUNT = 14;

    /** 驱动名的显示顺序，自检报告用。 */
    static final String[] DRIVER_NAMES = {
        "head_rot", "head_dx", "head_dy", "hair_l", "hair_r", "hair_f", "tail", "arm_l", "arm_r", "hand_l", "hand_r", "breath", "blink", "wind"
    };

    /** 各驱动的裁剪上限（绝对值）。 */
    static final float[] DRIVER_LIMIT = {
        MAX_HEAD_ROT, MAX_HEAD_DX, MAX_HEAD_DY, MAX_HAIR_L, MAX_HAIR_R, MAX_HAIR_F, MAX_TAIL, MAX_ARM_L, MAX_ARM_R, MAX_HAND_L, MAX_HAND_R, MAX_BREATH, MAX_BLINK, MAX_WIND
    };
}

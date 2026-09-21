package com.coco.balancebubble;

/**
 * 角色动作清单。
 *
 * <p>纯逻辑，不依赖 Android，方便单测覆盖（时长、待机池等）。
 * 角色是一张整图：整体姿态（位移、旋转、缩放）由动作表驱动，
 * 局部形变（眨眼、发丝随风、甩尾、挥手）由 {@link PartRig} 的网格负责。
 */
public enum PetAction {

    /** 站立待机（轻微呼吸）。 */
    IDLE(0L),
    /** 漂浮：上下缓慢浮沉。 */
    FLOAT(2600L),
    /** 蹦跳：跳起后落地挤压。 */
    BOUNCE(1100L),
    /** 摇摆：左右小幅摆动。 */
    SWAY(2000L),
    /** 歪头。 */
    LEAN(1600L),
    /** 摇头。 */
    SHAKE(1200L),
    /** 点头。 */
    NOD(1000L),
    /** 原地转一圈。 */
    SPIN(1500L),
    /** 伸懒腰：拉高。 */
    STRETCH(1800L),
    /** 缩一下（蹲下）。 */
    DUCK(1000L),
    /** 弹一下（被吓到）。 */
    POP(700L),
    /** 打瞌睡：慢慢下沉＋轻轻歪。 */
    SLEEPY(3000L),
    /** 甩尾：两侧长发左右摆动（局部形变，不是整体旋转）。 */
    WAG(1500L),
    /** 挥手：右臂绕腕点转动。 */
    WAVE(1600L);

    private final long ms;

    PetAction(long ms) {
        this.ms = ms;
    }

    /** 动作时长（毫秒）；IDLE 返回 0。 */
    public long duration() {
        return ms;
    }

    /** 待机时随机挑动作的池子（不含 IDLE，也不要太闹的 SPIN）。 */
    public static PetAction[] idlePool() {
        return new PetAction[]{FLOAT, SWAY, LEAN, NOD, BOUNCE, STRETCH, DUCK, SLEEPY, WAG, WAVE};
    }

    /** 动作数量（不含 IDLE）。 */
    public static int count() {
        return values().length - 1;
    }
}

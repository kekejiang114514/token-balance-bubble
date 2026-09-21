package com.coco.balancebubble;

/**
 * 角色动作清单。
 *
 * <p>刻意做成独立的纯 Java 枚举（不依赖 Android），这样语料与动作的对应关系
 * 能在 JVM 上直接跑单测验证。
 */
public enum PetAction {
    IDLE,       // 站立待机
    WAVE,       // 挥手
    HAPPY,      // 双手举起＋小跳
    NOD,        // 点头
    SHAKE,      // 摇头
    JUMP,       // 蹦一下
    SWIM,       // 摆尾游动
    SLEEPY,     // 打瞌睡
    LOOK,       // 左右张望
    SURPRISE;   // 被吓一跳

    /** 一次播放的时长（毫秒）。IDLE 不播放，返回 0。 */
    public long duration() {
        switch (this) {
            case WAVE: return 1800L;
            case HAPPY: return 2000L;
            case NOD: return 1000L;
            case SHAKE: return 1200L;
            case JUMP: return 1000L;
            case SWIM: return 2200L;
            case SLEEPY: return 2600L;
            case LOOK: return 1500L;
            case SURPRISE: return 800L;
            default: return 0L;
        }
    }

    /** 待机时随机挑的动作池（不含 IDLE，也不含惊吓这种偶发动作）。 */
    public static PetAction[] idlePool() {
        return new PetAction[]{WAVE, SWIM, LOOK, NOD, HAPPY, SHAKE, JUMP, SLEEPY};
    }
}

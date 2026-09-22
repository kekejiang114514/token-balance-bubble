import com.coco.balancebubble.PetAction;

/**
 * 采样时间轴：「帧号 → (动作, 进度)」的一张表，RigAnimTest 与 PartRigTest 共用。
 *
 * <p>两个测试必须跑**同一条时间轴**：一个负责把每帧的驱动量落盘当回归基线，
 * 另一个负责在真实帧上查网格会不会折叠。各写一份时间轴的话，
 * 迟早会变成「测的动画」和「存的动画」不是同一段。
 *
 * <p>结构：IDLE 开场 1s → 每个动作做一遍，动作之间插 3s 纯待机 → 收尾 4s 纯待机。
 * 动作间隔够长是为了让二阶跟随（发丝/尾鳍）真的能停下来，而不是一直在余振里。
 */
final class RigAnim {

    static final long DT_MS = 33L;
    static final long T0_MS = 1000L;
    static final long IDLE_GAP_MS = 3000L;
    static final long TAIL_MS = 4000L;

    private static final PetAction[] ACTIONS;
    private static final long[] START;
    private static final long[] DUR;
    private static final long END_MS;

    /** 总帧数（含收尾待机）。 */
    static final int FRAMES;

    static {
        PetAction[] all = PetAction.values();
        int n = 0;
        for (PetAction a : all) {
            if (a != PetAction.IDLE) n++;
        }
        ACTIONS = new PetAction[n];
        START = new long[n];
        DUR = new long[n];
        long now = T0_MS;
        int k = 0;
        for (PetAction a : all) {
            if (a == PetAction.IDLE) continue;
            ACTIONS[k] = a;
            START[k] = now;
            DUR[k] = a.duration();
            now += DUR[k] + IDLE_GAP_MS;
            k++;
        }
        END_MS = now + TAIL_MS;
        FRAMES = (int) ((END_MS - T0_MS) / DT_MS);
    }

    /** 第 frame 帧的时刻。 */
    static long now(int frame) {
        return T0_MS + frame * DT_MS;
    }

    /** 该时刻正在播的动作（不在动作段里就是 IDLE）。 */
    static PetAction actionAt(long t) {
        for (int k = 0; k < ACTIONS.length; k++) {
            if (t >= START[k] && t < START[k] + DUR[k]) return ACTIONS[k];
        }
        return PetAction.IDLE;
    }

    /** 该时刻的动作进度；没有在播动作时返回 -1（与 PetView 的约定一致）。 */
    static float progressAt(long t) {
        for (int k = 0; k < ACTIONS.length; k++) {
            if (t >= START[k] && t < START[k] + DUR[k]) {
                return (t - START[k]) / (float) DUR[k];
            }
        }
        return -1f;
    }

    private RigAnim() { }
}

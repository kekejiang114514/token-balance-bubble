import com.coco.balancebubble.PartRig;
import com.coco.balancebubble.PetAction;

import java.io.PrintWriter;
import java.util.Locale;

/**
 * 动画层采样：真的把 {@code PartRig.update()} 按 33ms 一帧跑完「每个动作 + 长待机」，
 * 把每帧的 14 个驱动量落盘到 {@code test/rig_anim_samples.txt}。
 *
 * <p>为什么需要它：模型侧的 {@code verify_rig.py} 只测「给定驱动量 → 位移」，
 * 测不出「动画层到底喂了多少驱动量」。v1.6 的甩尾就是模型完好、动画只喂了 23% 上限，
 * 屏幕上不动，被反馈成「尾巴不晃、头发不飘、动作生硬」。
 * 表格落盘后由 {@code verify_rig.py} 的 [G] 节按「可见性 / 形变安全 / 驱动用量」三条判据检查。
 *
 * <p>动画必须是确定性的（{@code PartRig} 内置固定种子的 xorshift），
 * 否则表格每次跑都不一样，没法当回归基线。
 */
public class RigAnimTest {

    static final int W = 692, H = 720;
    static final long DT_MS = 33L;
    static final long IDLE_GAP_MS = 3000L;

    static final String[] PROBES = {
        "top", "face", "neck", "chest", "belly", "tail", "hairL",
        "hairR", "hairF", "handL", "handR", "eyeL", "eyeR",
    };
    static final float[][] PP = {
        {300f, 24f}, {295f, 375f}, {295f, 430f}, {295f, 470f}, {295f, 545f}, {685f, 464f},
        {43f, 626f}, {492f, 684f}, {458f, 430f}, {214f, 498f}, {378f, 495f}, {226f, 336f},
        {366f, 329f},
    };

    static int fails = 0;
    static PartRig rig = new PartRig();

    static void ok(String name, boolean cond) {
        System.out.println((cond ? "  PASS " : "  FAIL ") + name);
        if (!cond) { fails++; }
    }

    static String f(float v) { return String.format(Locale.US, "%.6f", v); }

    public static void main(String[] args) throws Exception {
        int dc = PartRig.driverCount();
        String[] dnames = PartRig.driverNames();
        float[] limit = new float[dc];
        for (int i = 0; i < dc; i++) limit[i] = PartRig.driverLimit(i);

        // ---- 时间轴：见 RigAnim（PartRigTest 用的是同一条） ----
        int frames = RigAnim.FRAMES;

        float[] peakDrv = new float[dc];
        float[] peakProbe = new float[PROBES.length];
        float[] peakByAction = new float[PetAction.values().length * PROBES.length];
        boolean finite = true;
        boolean inRange = true;
        float maxAbsDrv = 0f;

        StringBuilder body = new StringBuilder(frames * 160);
        for (int i = 0; i < frames; i++) {
            long t = RigAnim.now(i);
            PetAction act = RigAnim.actionAt(t);
            float prog = RigAnim.progressAt(t);
            rig.update(t, act, prog, true);
            float[] d = rig.drivers();

            for (int k = 0; k < dc; k++) {
                if (!isFinite(d[k])) finite = false;
                float a = Math.abs(d[k]);
                if (a > peakDrv[k]) peakDrv[k] = a;
                if (a > limit[k] + 1e-4f) inRange = false;
                if (a > maxAbsDrv) maxAbsDrv = a;
            }
            for (int p = 0; p < PROBES.length; p++) {
                float m = (float) Math.hypot(rig.dxAt(PP[p][0], PP[p][1]), rig.dyAt(PP[p][0], PP[p][1]));
                if (m > peakProbe[p]) peakProbe[p] = m;
                int ai = act.ordinal() * PROBES.length + p;
                if (m > peakByAction[ai]) peakByAction[ai] = m;
            }

            body.append("#frame ").append(t).append(' ').append(act.name())
                .append(' ').append(f(prog));
            for (int k = 0; k < dc; k++) { body.append(' ').append(f(d[k])); }
            body.append('\n');
        }

        PrintWriter w = new PrintWriter("test/rig_anim_samples.txt", "UTF-8");
        w.println("#rig-anim 1");
        w.println("#frames " + frames);
        w.println("#dt " + DT_MS);
        w.println("#drivers " + join(dnames));
        StringBuilder lb = new StringBuilder();
        for (int i = 0; i < dc; i++) { lb.append(i == 0 ? "" : " ").append(f(limit[i])); }
        w.println("#driver-limit " + lb);
        for (int p = 0; p < PROBES.length; p++) {
            w.println("#probe " + PROBES[p] + " " + f(PP[p][0]) + " " + f(PP[p][1]));
        }
        for (int p = 0; p < PROBES.length; p++) {
            w.println("#probe-peak " + PROBES[p] + " " + f(peakProbe[p]));
        }
        for (int k = 0; k < dc; k++) {
            w.println("#driver-peak " + dnames[k] + " " + f(peakDrv[k]));
        }
        for (PetAction a : PetAction.values()) {
            if (a == PetAction.IDLE) continue;
            for (int p = 0; p < PROBES.length; p++) {
                w.println("#action-peak " + a.name() + " " + PROBES[p] + " "
                          + f(peakByAction[a.ordinal() * PROBES.length + p]));
            }
        }
        w.print(body);
        w.close();

        System.out.println("[动画采样] " + frames + " 帧 × " + dc + " 驱动 → test/rig_anim_samples.txt");
        ok("全部驱动都是有限值", finite);
        ok("驱动都没越出上限（最大 |值| " + String.format(Locale.US, "%.3f", maxAbsDrv) + "）", inRange);
        int used = 0;
        StringBuilder weak = new StringBuilder();
        for (int k = 0; k < dc; k++) {
            float frac = peakDrv[k] / limit[k];
            if (frac >= 0.55f) { used++; } else { weak.append(' ').append(dnames[k]).append('=').append(String.format(Locale.US, "%.2f", frac)); }
        }
        ok("每个驱动都至少用掉 55% 上限（" + used + "/" + dc + " 达标；不足：" + (weak.length() == 0 ? "无" : weak.toString().trim()) + "）",
           used == dc);
        ok("采样确实在动（尾梢峰值 " + String.format(Locale.US, "%.1f", peakProbe[5]) + "px）", peakProbe[5] > 8f);
        if (fails > 0) {
            System.out.println("RigAnimTest: " + fails + " 项失败");
            System.exit(1);
        }
        System.out.println("RigAnimTest: 全部通过");
    }

    static boolean isFinite(float v) { return !Float.isNaN(v) && !Float.isInfinite(v); }

    static String join(String[] a) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < a.length; i++) { if (i > 0) b.append(' '); b.append(a[i]); }
        return b.toString();
    }
}

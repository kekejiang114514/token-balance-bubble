import com.coco.balancebubble.PartRig;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * 跨语言黄金表：Java 的位移场/权重场必须与 Python（tools/rig_math.py）逐点一致。
 *
 * <p>为什么需要这个测试：模型（骨表、权重场参数、幅度上限）的事实来源在 Python，
 * Java 侧只是翻译。一旦翻译走样（漏一处 cos-1、少乘一个增益、下标写错），
 * 单侧单测都发现不了——只有把两边的数值摆在一起比才能抓住。
 * 表由 {@code python3 tools/verify_rig.py} 生成（它自己会先重算一遍做自洽检查）。
 *
 * <p>阈值说明：Java 用 float、Python 用 float64，逐点误差应在 1e-3 px 量级；
 * 真正要抓的错误（公式/常量写错）偏差是「像素级」的，所以容差取 0.02 px 仍有足够区分度。
 */
public class RigGoldenTest {

    static final File TABLE = new File("test/rig_golden.txt");
    static final float TOL_DISP = 0.02f;   // 位移容差（贴图像素）
    static final float TOL_W = 2e-4f;      // 权重容差

    static int fails = 0;

    static void ok(String name, boolean cond) {
        System.out.println((cond ? "  PASS " : "  FAIL ") + name);
        if (!cond) fails++;
    }

    /** 一个状态：名字 + 14 个驱动量。 */
    static final class State {
        String name;
        float[] drv;
    }

    public static void main(String[] args) throws Exception {
        if (!TABLE.isFile()) {
            System.out.println("  FAIL 缺少黄金表 " + TABLE.getPath()
                    + "（先跑 python3 tools/verify_rig.py）");
            System.exit(1);
        }
        List<State> states = new ArrayList<State>();
        List<float[]> pts = new ArrayList<float[]>();     // x,y,dx,dy,w0..w11
        List<Integer> owner = new ArrayList<Integer>();   // 每个点属于哪个状态
        String[] drvNames = null;
        String[] wNames = null;
        String srcHash = null;

        BufferedReader in = new BufferedReader(new InputStreamReader(
                new FileInputStream(TABLE), "UTF-8"));
        String line;
        while ((line = in.readLine()) != null) {
            if (line.length() == 0) continue;
            String[] f = line.split("\\s+");
            if (f[0].equals("#")) {
                if (f.length > 2 && f[1].equals("drivers")) drvNames = tail(f, 2);
                else if (f.length > 2 && f[1].equals("weights")) wNames = tail(f, 2);
                else if (f.length > 3 && f[1].equals("source")) srcHash = f[3];
            } else if (f[0].equals("S")) {
                State s = new State();
                s.name = f[1];
                s.drv = new float[f.length - 2];
                for (int i = 2; i < f.length; i++) s.drv[i - 2] = Float.parseFloat(f[i]);
                states.add(s);
            } else if (f[0].equals("P")) {
                float[] v = new float[f.length - 1];
                for (int i = 1; i < f.length; i++) v[i - 1] = Float.parseFloat(f[i]);
                pts.add(v);
                owner.add(states.size() - 1);
            }
        }
        in.close();

        ok("表头驱动名 " + (drvNames == null ? "缺失" : drvNames.length + " 个"),
                drvNames != null && drvNames.length == PartRig.driverCount());
        ok("表头权重名 " + (wNames == null ? "缺失" : wNames.length + " 个"),
                wNames != null && wNames.length == PartRig.W_COUNT);
        ok("表带来源指纹 " + (srcHash == null ? "缺失（表已过期，重跑 verify_rig.py）"
                : srcHash.substring(0, 12) + "…"), srcHash != null);
        ok("状态数 " + states.size(), states.size() > 30);
        ok("采样点数 " + pts.size(), pts.size() == 169 * states.size());
        if (fails > 0) System.exit(1);

        PartRig rig = new PartRig();
        float[] w = new float[PartRig.W_COUNT];
        float worstD = 0f, worstW = 0f;
        String atD = "", atW = "";
        int nStates = 0;
        int cur = -1;
        int dup = 0;

        for (int k = 0; k < pts.size(); k++) {
            int si = owner.get(k);
            if (si != cur) {
                cur = si;
                nStates++;
                rig.setDrivers(states.get(si).drv);
            }
            float[] p = pts.get(k);
            float x = p[0], y = p[1];
            float dx = rig.dxAt(x, y), dy = rig.dyAt(x, y);
            float d = Math.max(Math.abs(dx - p[2]), Math.abs(dy - p[3]));
            if (d > worstD) { worstD = d; atD = states.get(si).name + "@" + (int) x + "," + (int) y; }
            // dxAt 与 dyAt 都会重算整场，这里只是重复取一遍权重（结果一致）
            w = rig.weightsAt(x, y);
            for (int i = 0; i < PartRig.W_COUNT; i++) {
                float e = Math.abs(w[i] - p[4 + i]);
                if (e > worstW) {
                    worstW = e;
                    atW = wNames[i] + "@" + states.get(si).name + "+" + (int) x + "," + (int) y;
                }
            }
            if (states.get(si).drv.length != PartRig.driverCount()) dup++;
        }
        ok("每状态驱动个数 " + PartRig.driverCount(), dup == 0);
        ok("覆盖状态 " + nStates, nStates == states.size());

        System.out.println("    位移最大偏差 " + worstD + " px @" + atD);
        System.out.println("    权重最大偏差 " + worstW + " @" + atW);
        ok("位移逐点一致（≤" + TOL_DISP + "px）", worstD <= TOL_DISP);
        ok("权重逐点一致（≤" + TOL_W + "）", worstW <= TOL_W);

        System.out.println(fails == 0 ? "RigGoldenTest 全部通过" : "RigGoldenTest 失败 " + fails + " 项");
        if (fails > 0) System.exit(1);
    }

    static String[] tail(String[] f, int from) {
        String[] out = new String[f.length - from];
        System.arraycopy(f, from, out, 0, out.length);
        return out;
    }
}

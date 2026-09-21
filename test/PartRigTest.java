import com.coco.balancebubble.PartRig;
import com.coco.balancebubble.PetAction;

/** PartRig 纯逻辑单测：网格、权重场、保护区、眨眼、挥手、甩尾、形变连续性、长时间稳定性。 */
public class PartRigTest {

    static int fails = 0;
    static final int MW = PartRig.meshWidth(), MH = PartRig.meshHeight();
    static final float RAW_W = 1022f, RAW_H = 1062f;
    static PartRig rig = new PartRig();
    static float[] verts = new float[(MW + 1) * (MH + 1) * 2];

    static void ok(String name, boolean cond) {
        System.out.println((cond ? "  PASS " : "  FAIL ") + name);
        if (!cond) { fails++; }
    }

    static void near(String name, float got, float want, float tol) {
        ok(name + " got=" + r1(got) + " want=" + r1(want) + " tol=" + tol,
           Math.abs(got - want) <= tol);
    }

    static String r1(float v) { return String.valueOf(Math.round(v * 10) / 10f); }

    /** 基准位置（未形变时的顶点坐标） */
    static float bx(int i) { return i * RAW_W / MW; }
    static float by(int j) { return j * RAW_H / MH; }
    static float gx(int i, int j) { return verts[(j * (MW + 1) + i) * 2]; }
    static float gy(int i, int j) { return verts[(j * (MW + 1) + i) * 2 + 1]; }
    /** 离给定贴图像素最近的网格列/行 */
    static int ix(float x) { return Math.round(x * MW / RAW_W); }
    static int iy(float y) { return Math.round(y * MH / RAW_H); }
    static float dvx(int i, int j) { return gx(i, j) - bx(i); }
    static float dvy(int i, int j) { return gy(i, j) - by(j); }

    /** 跑一帧并返回顶点数组（dst 即原图尺寸，顶点坐标=贴图像素坐标+位移） */
    static float[] frame(long now, PetAction a, float progress) {
        rig.update(now, a, progress, true);
        rig.fill(0f, 0f, RAW_W, RAW_H, verts);
        return verts;
    }

    static float[] neutralFrame() {
        rig.update(0L, PetAction.IDLE, -1f, false);
        rig.fill(0f, 0f, RAW_W, RAW_H, verts);
        return verts;
    }

    /** 指定各驱动量跑一帧（确定性验证用） */
    static float[] forcedFrame(float blink, float wind, float phase, float boost,
                               float wagDeg, float waveDeg) {
        rig.update(0L, PetAction.IDLE, -1f, true);
        rig.overrideForTest(blink, wind, phase, boost, wagDeg, waveDeg);
        rig.fill(0f, 0f, RAW_W, RAW_H, verts);
        return verts;
    }

    public static void main(String[] args) {
        meshShape();
        weights();
        protectedAreas();
        blink();
        wave();
        wag();
        continuity();
        stability();
        if (fails > 0) {
            System.out.println("PartRigTest: " + fails + " 项失败");
            System.exit(1);
        }
        System.out.println("PartRigTest: 全部通过（" + (MW + 1) * (MH + 1) + " 顶点）");
    }

    static void meshShape() {
        System.out.println("[网格]");
        ok("宽高为 26x26", MW == 26 && MH == 26);
        ok("顶点数 729", (MW + 1) * (MH + 1) == 729);
        neutralFrame();
        near("静止时左上顶点 x", gx(0, 0), 0f, 0.001f);
        near("静止时右下顶点 x", gx(MW, MH), RAW_W, 0.01f);
        near("静止时右下顶点 y", gy(MW, MH), RAW_H, 0.01f);
        ok("静止判定为中性（可走普通绘制）", rig.isNeutral());
        float maxOff = 0f;
        for (int j = 0; j <= MH; j++) {
            for (int i = 0; i <= MW; i++) {
                maxOff = Math.max(maxOff, Math.abs(gx(i, j) - bx(i)));
                maxOff = Math.max(maxOff, Math.abs(gy(i, j) - by(j)));
            }
        }
        near("静止帧所有顶点都贴合基准网格", maxOff, 0f, 0.001f);
        forcedFrame(0f, 1f, 1.0f, 1f, 0f, 0f);
        ok("有风时不再是中性", !rig.isNeutral());
    }

    static void weights() {
        System.out.println("[权重场]");
        near("左眼中心权重", PartRig.weightEyeLeft(335f, 494f), 1f, 0.02f);
        near("右眼中心权重", PartRig.weightEyeRight(540f, 490f), 1f, 0.02f);
        near("左眼场不碰右眼", PartRig.weightEyeLeft(540f, 490f), 0f, 0.001f);
        near("右眼场不碰左眼", PartRig.weightEyeRight(335f, 494f), 0f, 0.001f);
        near("画面左上角无眼睛权重", PartRig.weightEyeLeft(60f, 60f), 0f, 0.001f);
        ok("头顶外圈有发丝权重", PartRig.weightHead(438f, 20f) > 0.5f);
        near("头部中心（脸内）无发丝权重", PartRig.weightHead(438f, 300f), 0f, 0.001f);
        near("眼睛以下无发丝权重", PartRig.weightHead(438f, 520f), 0f, 0.001f);
        ok("左尾梢权重高", PartRig.weightTailLeft(200f, 710f) > 0.6f);
        ok("右发丝外侧权重高", PartRig.weightTailRight(830f, 690f) > 0.3f);
        near("左手处无尾巴权重", PartRig.weightTailLeft(316f, 733f), 0f, 0.001f);
        near("右手中心权重", PartRig.weightHandRight(558f, 730f), 1f, 0.02f);
        near("左手中心权重", PartRig.weightHandLeft(316f, 733f), 1f, 0.02f);
        float maxStep = 0f;
        for (int y = 0; y <= 1062; y += 6) {
            float prev = 0f;
            for (int x = 0; x <= 1022; x += 6) {
                float w = Math.max(PartRig.weightTailLeft(x, y), PartRig.weightTailRight(x, y));
                w = Math.max(w, PartRig.weightHead(x, y));
                if (x > 0) maxStep = Math.max(maxStep, Math.abs(w - prev));
                prev = w;
            }
        }
        ok("权值场没有硬边（6 像素步长最大变化 " + r1(maxStep * 1000) + "/1000）",
           maxStep <= 0.45f);
    }

    static void protectedAreas() {
        System.out.println("[保护区]");
        near("脸部左下（落在尾箱里）无尾权重", PartRig.weightTailLeft(280f, 640f), 0f, 0.001f);
        near("脸部右下无尾权重", PartRig.weightTailRight(600f, 640f), 0f, 0.001f);
        near("右手附近无发丝权重", PartRig.weightHead(558f, 640f), 0f, 0.001f);
        ok("对照：同一高度脸部外侧确有尾权重", PartRig.weightTailLeft(200f, 640f) > 0.1f);
        int moved = 0;
        for (float y = 380f; y <= 630f; y += 10f) {
            for (float x = 270f; x <= 600f; x += 10f) {
                float w = Math.max(PartRig.weightTailLeft(x, y), PartRig.weightTailRight(x, y));
                if (w > 0f) moved++;
            }
        }
        ok("脸部范围内一个点都不会被尾巴带走（实测 " + moved + " 处）", moved == 0);
    }

    static void blink() {
        System.out.println("[眨眼]");
        int ei = ix(335f), ej = iy(494f);   // 离左眼中心最近的网格顶点
        neutralFrame();
        near("睁眼时眼点不动", dvy(ei, ej), 0f, 0.001f);
        forcedFrame(1f, 0f, 0f, 1f, 0f, 0f);
        float dy1 = dvy(ei, ej);
        ok("闭眼时眼点朝睑线塌陷（实测 " + r1(dy1) + "px）", dy1 > 30f);
        ok("闭眼时横向只轻微内收（实测 " + r1(Math.abs(dvx(ei, ej))) + "px）",
           Math.abs(dvx(ei, ej)) < 6f);
        forcedFrame(0.5f, 0f, 0f, 1f, 0f, 0f);
        near("半闭的位移正好是闭合的一半", dvy(ei, ej) / dy1, 0.5f, 0.05f);
        forcedFrame(1f, 0f, 0f, 1f, 0f, 0f);
        near("眨眼不影响尾梢", dvy(ix(200f), iy(710f)), 0f, 0.001f);
        near("眨眼不影响头顶", dvy(ix(438f), iy(20f)), 0f, 0.001f);
        rig.forceBlinkAt(1000L);
        rig.update(1000L, PetAction.IDLE, -1f, true);
        near("眨眼刚发生时为 0", rig.blinkAmount(1000L), 0f, 0.02f);
        near("眨眼峰值接近 1", rig.blinkAmount(1095L), 1f, 0.01f);
        near("眨眼结束后归零", rig.blinkAmount(1300L), 0f, 0.001f);
    }

    static void wave() {
        System.out.println("[挥手]");
        int ri = ix(558f), rj = iy(730f);   // 右手
        int li = ix(316f), lj = iy(733f);   // 左手
        forcedFrame(0f, 0f, 0f, 1f, 0f, 0f);
        near("没有挥手时右手不动", dvx(ri, rj), 0f, 0.001f);
        forcedFrame(0f, 0f, 0f, 1f, 0f, 27f);
        float rdx = dvx(ri, rj), ldx = dvx(li, lj);
        ok("挥手时右手横向摆开（实测 " + r1(rdx) + "px）", rdx < -8f);
        ok("左手只跟着晃一点（" + r1(Math.abs(ldx)) + "px = 右手的 "
           + Math.round(Math.abs(ldx / rdx) * 100) + "%）",
           Math.abs(ldx) > 1.5f && Math.abs(ldx) < Math.abs(rdx) * 0.6f);
        near("腕点几乎原地不动", Math.abs(dvx(ix(558f), iy(697f))), 0f, 3f);
        forcedFrame(0f, 0f, 0f, 1f, 0f, -27f);
        ok("反向挥手位移反号", dvx(ri, rj) > 8f);
        forcedFrame(0f, 0f, 0f, 1f, 0f, 27f);
        near("挥手不带动脸部", dvx(ix(438f), iy(505f)), 0f, 0.001f);
    }

    static void wag() {
        System.out.println("[甩尾]");
        int ti = ix(196f), tj = iy(694f);      // 左发梢
        int si = ix(747f), sj = iy(653f);      // 右发丝
        int ri = ix(275f), rj = iy(694f);      // 同高度、更靠根部
        forcedFrame(0f, 0f, 0f, 1f, 0f, 0f);
        near("没有甩尾时发梢不动", dvx(ti, tj), 0f, 0.001f);
        forcedFrame(0f, 0f, 0f, 1f, 15f, 0f);
        float dx = dvx(ti, tj), dy = dvy(ti, tj);
        ok("左发梢摆向一侧（实测 " + r1(dx) + "px）", dx < -8f);
        ok("摆动轨迹带一点上提（实测 " + r1(dy) + "px）", dy < -2f);
        float sdx = dvx(si, sj);
        ok("右侧长发朝同一侧摆（实测 " + r1(sdx) + "px）", sdx < -3f);
        ok("右摆幅小于左（|" + r1(sdx) + "| < |" + r1(dx) + "|）", Math.abs(sdx) < Math.abs(dx));
        int ui = ix(236f), uj = iy(776f);   // 同一行、更靠内侧
        int vi = ix(196f), vj = iy(776f);   // 靠外的发梢
        float outer = Math.abs(dvx(vi, vj)), inner = Math.abs(dvx(ui, uj));
        ok("越靠内侧摆得越少（内 " + r1(inner) + "px / 梢 " + r1(outer) + "px = "
           + Math.round(inner / outer * 100) + "%）", inner > 5f && inner < outer * 0.75f);
        near("甩尾不带动脸部", dvx(ix(438f), iy(505f)), 0f, 0.001f);
        forcedFrame(0f, 0f, 0f, 1f, -15f, 0f);
        ok("反向甩尾位移反号", dvx(ti, tj) > 8f);
        forcedFrame(0f, 1f, 1.0f, 1f, 0f, 0f);
        float w1 = Math.abs(dvx(ti, tj));
        forcedFrame(0f, 1f, 1.0f, 2.1f, 0f, 0f);
        float w2 = Math.abs(dvx(ti, tj));
        ok("甩尾时风被放大（" + r1(w1) + " → " + r1(w2) + "px）", w2 > w1 * 1.8f);
    }

    static void continuity() {
        System.out.println("[连续性]");
        forcedFrame(0.8f, 1f, 1.3f, 1.5f, 15f, 27f);
        float minCellX = 1e9f, maxCellX = 0f, minCellY = 1e9f, maxCellY = 0f;
        for (int j = 0; j <= MH; j++) {
            for (int i = 0; i < MW; i++) {
                float d = gx(i + 1, j) - gx(i, j);
                if (d < minCellX) minCellX = d;
                if (d > maxCellX) maxCellX = d;
            }
        }
        for (int i = 0; i <= MW; i++) {
            for (int j = 0; j < MH; j++) {
                float d = gy(i, j + 1) - gy(i, j);
                if (d < minCellY) minCellY = d;
                if (d > maxCellY) maxCellY = d;
            }
        }
        float cw = RAW_W / MW, ch = RAW_H / MH;
        ok("网格不折叠（行内 x 始终递增，最小格宽 " + r1(minCellX) + "/" + r1(cw) + "px）",
           minCellX > cw * 0.15f);
        ok("网格不折叠（列内 y 始终递增，最小格高 " + r1(minCellY) + "/" + r1(ch) + "px）",
           minCellY > ch * 0.15f);
        ok("形变不夸张（最大格宽 " + r1(maxCellX) + " < 3×" + r1(cw) + "px）",
           maxCellX < cw * 3f);
        ok("形变不夸张（最大格高 " + r1(maxCellY) + " < 3×" + r1(ch) + "px）",
           maxCellY < ch * 3f);
    }

    static void stability() {
        System.out.println("[稳定性]");
        boolean finite = true;
        float maxAbs = 0f, motion = 0f;
        for (int f = 0; f < 240; f++) {
            long now = 1000L + f * 33L;
            PetAction a = f % 3 == 0 ? PetAction.WAG : (f % 3 == 1 ? PetAction.WAVE : PetAction.IDLE);
            float prog = (a == PetAction.IDLE) ? -1f : (f % 17) / 17f;
            frame(now, a, prog);
            for (int k = 0; k < verts.length; k++) {
                if (Float.isNaN(verts[k]) || Float.isInfinite(verts[k])) finite = false;
                maxAbs = Math.max(maxAbs, Math.abs(verts[k]));
            }
            motion += Math.abs(dvx(ix(196f), iy(694f)));
        }
        ok("240 帧内顶点都是有限值", finite);
        ok("240 帧内顶点都没跑出画面（最远 " + Math.round(maxAbs) + "px）", maxAbs < RAW_W + 200f);
        ok("240 帧里尾梢一直在动（累计 " + Math.round(motion) + "px）", motion > 200f);
        neutralFrame();
        ok("关掉动画后回到中性", rig.isNeutral());
        near("关掉动画后尾梢归位", dvx(ix(196f), iy(694f)), 0f, 0.001f);
    }
}

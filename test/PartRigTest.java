import com.coco.balancebubble.PartRig;
import com.coco.balancebubble.PetAction;

/**
 * PartRig 纯逻辑单测：网格形态、权重场、保护区、眨眼、挥手、甩尾、呼吸/头部位移、
 * 形变连续性、长时间稳定性。
 *
 * <p>坐标一律用「贴图像素」（画布 {@value #W}×{@value #H}），与 tools/verify_rig.py 的
 * PROBE 表同一套点位；同一个现象在两个语言里都用同一组探针量，改贴图时一起改。
 */
public class PartRigTest {

    static int fails = 0;
    static final int MW = PartRig.meshWidth(), MH = PartRig.meshHeight();
    static final float W = 692f, H = 720f;
    static PartRig rig = new PartRig();
    static float[] verts = new float[(MW + 1) * (MH + 1) * 2];

    // ---- 驱动下标（按名字取，避免和模型里的顺序脱钩） ----
    static final int D_HEAD_ROT = PartRig.driverIndex("head_rot");
    static final int D_HEAD_DX = PartRig.driverIndex("head_dx");
    static final int D_HEAD_DY = PartRig.driverIndex("head_dy");
    static final int D_HAIR_L = PartRig.driverIndex("hair_l");
    static final int D_HAIR_R = PartRig.driverIndex("hair_r");
    static final int D_HAIR_F = PartRig.driverIndex("hair_f");
    static final int D_TAIL = PartRig.driverIndex("tail");
    static final int D_ARM_L = PartRig.driverIndex("arm_l");
    static final int D_ARM_R = PartRig.driverIndex("arm_r");
    static final int D_HAND_L = PartRig.driverIndex("hand_l");
    static final int D_HAND_R = PartRig.driverIndex("hand_r");
    static final int D_BREATH = PartRig.driverIndex("breath");
    static final int D_BLINK = PartRig.driverIndex("blink");
    static final int D_WIND = PartRig.driverIndex("wind");

    // ---- 探针（贴图像素）----
    // 与 tools/verify_rig.py:PROBE 必须逐点相同：判据只认这些坐标。
    // 旧版这里写的是手画坐标系里的点，场重做之后 (660,660) 落进了右发丝的覆盖区，
    // (150,402)/(452,402) 落进了下巴和胸口的保护区，(295,375) 干脆是脸芯，
    // 于是「尾梢不动」「发丝不动」「额发能摆」一共 5 条同时假失败。
    // **改场之后要用 tools/probe_fit.py 重挑，然后两边一起改。**
    static final float[] P_TOP = {300f, 24f};
    static final float[] P_FACE = {295f, 375f};
    static final float[] P_CHEST = {295f, 470f};
    static final float[] P_BELLY = {295f, 545f};
    static final float[] P_TAIL = {685f, 464f};
    static final float[] P_HAIR_L = {43f, 626f};
    static final float[] P_HAIR_R = {492f, 684f};
    static final float[] P_HAIR_F = {458f, 430f};
    static final float[] P_HAND_L = {214f, 498f};
    static final float[] P_HAND_R = {378f, 495f};
    static final float[] P_EYE_L = {226f, 336f};
    static final float[] P_EYE_R = {366f, 329f};

    static void ok(String name, boolean cond) {
        System.out.println((cond ? "  PASS " : "  FAIL ") + name);
        if (!cond) { fails++; }
    }

    static void near(String name, float got, float want, float tol) {
        ok(name + " got=" + r1(got) + " want=" + r1(want) + " tol=" + tol,
           Math.abs(got - want) <= tol);
    }

    static String r1(float v) { return String.valueOf(Math.round(v * 10) / 10f); }
    static String r3(float v) { return String.valueOf(Math.round(v * 1000) / 1000f); }
    static boolean isFinite(float v) { return !Float.isNaN(v) && !Float.isInfinite(v); }

    /** 基准网格坐标 */
    static float bx(int i) { return i * W / MW; }
    static float by(int j) { return j * H / MH; }
    static float gx(int i, int j) { return verts[(j * (MW + 1) + i) * 2]; }
    static float gy(int i, int j) { return verts[(j * (MW + 1) + i) * 2 + 1]; }
    static int ix(float x) { return Math.round(x * MW / W); }
    static int iy(float y) { return Math.round(y * MH / H); }
    static float dvx(int i, int j) { return gx(i, j) - bx(i); }
    static float dvy(int i, int j) { return gy(i, j) - by(j); }

    /** 直接指定驱动量的确定性一帧 */
    static float[] forced(float[] pair) {
        rig.setDrivers(pair);
        rig.fill(0f, 0f, W, H, verts);
        return verts;
    }

    /** 单驱动取值的便捷写法 */
    static float[] one(int idx, float v) {
        float[] d = blank();
        d[idx] = v;
        return forced(d);
    }

    /** 全零驱动（含 wind/breath）：判据的基线，别让待机摆动混进「本动作带动了谁」 */
    static float[] blank() {
        return new float[PartRig.driverCount()];
    }

    static float ax(float[] p) { return rig.dxAt(p[0], p[1]); }
    static float ay(float[] p) { return rig.dyAt(p[0], p[1]); }
    static float amag(float[] p) { return (float) Math.hypot(ax(p), ay(p)); }

    /** 跑一帧动画并返回顶点数组 */
    static float[] frame(long now, PetAction a, float progress) {
        rig.update(now, a, progress, true);
        rig.fill(0f, 0f, W, H, verts);
        return verts;
    }

    public static void main(String[] args) {
        setup();
        meshShape();
        weights();
        protectedAreas();
        blink();
        wave();
        wag();
        headAndBreath();
        continuity();
        stability();
        if (fails > 0) {
            System.out.println("PartRigTest: " + fails + " 项失败");
            System.exit(1);
        }
        System.out.println("PartRigTest: 全部通过（" + (MW + 1) * (MH + 1) + " 顶点）");
    }

    static void setup() {
        ok("驱动表齐全（14 个索引都能取到）",
           D_HEAD_ROT >= 0 && D_HEAD_DX >= 0 && D_HEAD_DY >= 0 && D_HAIR_L >= 0
           && D_HAIR_R >= 0 && D_HAIR_F >= 0 && D_TAIL >= 0 && D_ARM_L >= 0 && D_ARM_R >= 0
           && D_HAND_L >= 0 && D_HAND_R >= 0 && D_BREATH >= 0 && D_BLINK >= 0 && D_WIND >= 0
           && PartRig.driverCount() == 14);
    }

    static void meshShape() {
        System.out.println("[网格]");
        ok("网格为 " + MW + "x" + MH, MW == 24 && MH == 24);
        ok("顶点数 " + (MW + 1) * (MH + 1), (MW + 1) * (MH + 1) == 625);
        rig.setDrivers(new float[PartRig.driverCount()]);
        rig.fill(0f, 0f, W, H, verts);
        near("静止时左上顶点 x", gx(0, 0), 0f, 0.001f);
        near("静止时右下顶点 x", gx(MW, MH), W, 0.01f);
        near("静止时右下顶点 y", gy(MW, MH), H, 0.01f);
        ok("零驱动判定为中性（可走普通绘制）", rig.isNeutral());
        float maxOff = 0f;
        for (int j = 0; j <= MH; j++) {
            for (int i = 0; i <= MW; i++) {
                maxOff = Math.max(maxOff, Math.abs(gx(i, j) - bx(i)));
                maxOff = Math.max(maxOff, Math.abs(gy(i, j) - by(j)));
            }
        }
        near("零驱动帧所有顶点都贴合基准网格", maxOff, 0f, 0.001f);
        one(D_WIND, 1f);
        ok("有风时不再是中性", !rig.isNeutral());
    }

    static void weights() {
        System.out.println("[权重场]");
        float[] w = rig.weightsAt(P_EYE_L[0], P_EYE_L[1]);
        near("左眼中心权重", w[PartRig.W_EYE_L], 1f, 0.02f);
        w = rig.weightsAt(P_EYE_R[0], P_EYE_R[1]);
        near("右眼中心权重", w[PartRig.W_EYE_R], 1f, 0.02f);
        near("左眼场不碰右眼", w[PartRig.W_EYE_L], 0f, 0.001f);
        w = rig.weightsAt(P_EYE_L[0], P_EYE_L[1]);
        near("右眼场不碰左眼", w[PartRig.W_EYE_R], 0f, 0.001f);
        near("画面左上角无眼睛权重", rig.weightsAt(8f, 8f)[PartRig.W_EYE_L], 0f, 0.001f);
        w = rig.weightsAt(P_TOP[0], P_TOP[1]);
        ok("头顶有头部权重（" + r1(w[PartRig.W_HEAD]) + "）", w[PartRig.W_HEAD] > 0.5f);
        near("脸内无头部权重", rig.weightsAt(295f, 500f)[PartRig.W_HEAD], 0f, 0.001f);
        near("头顶无躯干权重", w[PartRig.W_TORSO], 0f, 0.001f);
        ok("尾梢权重高（" + r1(rig.weightsAt(P_TAIL[0], P_TAIL[1])[PartRig.W_TAIL]) + "）",
           rig.weightsAt(P_TAIL[0], P_TAIL[1])[PartRig.W_TAIL] > 0.5f);
        near("左手中心权重", rig.weightsAt(P_HAND_L[0], P_HAND_L[1])[PartRig.W_HAND_L], 1f, 0.02f);
        near("右手中心权重", rig.weightsAt(P_HAND_R[0], P_HAND_R[1])[PartRig.W_HAND_R], 1f, 0.02f);
        near("右手处无左手权重", rig.weightsAt(P_HAND_R[0], P_HAND_R[1])[PartRig.W_HAND_L], 0f, 0.001f);

        float maxStep = 0f;
        for (float y = 0f; y <= H; y += 6f) {
            float prev = 0f;
            for (float x = 0f; x <= W; x += 6f) {
                float[] q = rig.weightsAt(x, y);
                float top = 0f;
                for (int k = 0; k < PartRig.W_COUNT; k++) top = Math.max(top, q[k]);
                if (x > 0f) maxStep = Math.max(maxStep, Math.abs(top - prev));
                prev = top;
            }
        }
        ok("权值场没有硬边（6 像素步长最大变化 " + r1(maxStep * 1000) + "/1000）", maxStep <= 0.45f);
    }

    static void protectedAreas() {
        System.out.println("[保护区]");
        near("脸部（尾箱内）无尾权重", rig.weightsAt(295f, 500f)[PartRig.W_TAIL], 0f, 0.001f);
        near("脸部（发箱内）无发丝权重", rig.weightsAt(295f, 500f)[PartRig.W_HAIR_R], 0f, 0.001f);
        ok("对照：同高度脸外侧确有尾权重",
           rig.weightsAt(P_TAIL[0], 500f)[PartRig.W_TAIL] > 0.1f);
        // 保护区用模型自己的脸芯椭圆（PartRig.faceCore()），不再手画方框：
        // 旧版硬编码 x∈[240,430] y∈[340,560]，场改过之后一半落在脸外，
        // 「脸被发丝带走 185 处」全是假失败。
        int moved = 0, total = 0;
        float mHair = 0f, mTail = 0f;
        float[] fc = PartRig.faceCore();
        for (float y = fc[1] - fc[3]; y <= fc[1] + fc[3]; y += 2f) {
            for (float x = fc[0] - fc[2]; x <= fc[0] + fc[2]; x += 2f) {
                if (!PartRig.inFace(x, y)) continue;
                total++;
                float[] q = rig.weightsAt(x, y);
                float h = q[PartRig.W_HAIR_L] + q[PartRig.W_HAIR_R] + q[PartRig.W_HAIR_F];
                mHair = Math.max(mHair, h);
                mTail = Math.max(mTail, q[PartRig.W_TAIL]);
                if (h > 0.02f || q[PartRig.W_TAIL] > 0.02f) moved++;
            }
        }
        ok("脸芯采样到了点（" + total + " 个）", total > 5000);
        ok("脸芯不被发丝/尾巴带走（最大发丝权 " + r3(mHair) + "、尾巴权 " + r3(mTail)
           + "，超 0.02 的 " + moved + " 处）", moved == 0);
        // 0.02 的权 × 20px 摆幅 = 0.4px，屏幕缩到 0.3 倍就是 0.12px，看不见。
        // 眼眶周围：眼球 66px 高，眨眼时如果有发丝场压在上面，眼睛会被糊掉。
        int bad = 0, cnt = 0;
        float[][] eyes = {P_EYE_L, P_EYE_R};
        for (float[] e : eyes) {
            for (float dy = -34f; dy <= 34f; dy += 2f) {
                for (float dx = -34f; dx <= 34f; dx += 2f) {
                    if (dx * dx + dy * dy > 34f * 34f) continue;
                    cnt++;
                    float[] q = rig.weightsAt(e[0] + dx, e[1] + dy);
                    if (q[PartRig.W_HAIR_L] + q[PartRig.W_HAIR_R] + q[PartRig.W_HAIR_F] > 0.05f) bad++;
                }
            }
        }
        ok("眼眶周围没有被发丝场覆盖（" + bad + "/" + cnt + " 处）", bad == 0);
    }

    static void blink() {
        System.out.println("[眨眼]");
        one(D_BLINK, 0f);
        near("睁眼时左眼不动", amag(P_EYE_L), 0f, 0.001f);
        one(D_BLINK, 1f);
        float dyL = ay(P_EYE_L), dxL = ax(P_EYE_L);
        ok("闭眼时眼点朝睑线塌陷（" + r1(dyL) + "px）", dyL > 20f);
        ok("闭眼时横向不漂（" + r1(dxL) + "px）", Math.abs(dxL) < 1f);
        ok("右眼同样闭合（" + r1(ay(P_EYE_R)) + "px）", ay(P_EYE_R) > 20f);
        float dy1 = dyL;
        one(D_BLINK, 0.5f);
        near("半闭位移正好是一半", ay(P_EYE_L) / dy1, 0.5f, 0.05f);
        one(D_BLINK, 1f);
        near("眨眼不带动尾梢", amag(P_TAIL), 0f, 0.001f);
        near("眨眼不带动头顶", amag(P_TOP), 0f, 0.001f);
        near("眨眼不带动胸口", amag(P_CHEST), 0f, 0.001f);
        // 时序：闭合 175ms，峰值在正中间
        rig.update(0L, PetAction.IDLE, -1f, false);
        rig.forceBlinkAt(1000L);
        rig.update(1000L, PetAction.IDLE, -1f, true);
        near("眨眼刚发生时为 0", rig.blinkNow(), 0f, 0.02f);
        rig.update(1088L, PetAction.IDLE, -1f, true);
        near("眨眼峰值接近 1", rig.blinkNow(), 1f, 0.02f);
        rig.update(1300L, PetAction.IDLE, -1f, true);
        near("眨眼结束后归零", rig.blinkNow(), 0f, 0.001f);
    }

    static void wave() {
        System.out.println("[挥手]");
        float lim = PartRig.driverLimit(D_HAND_R);
        one(D_HAND_R, 0f);
        near("没有挥手时右手不动", amag(P_HAND_R), 0f, 0.001f);
        one(D_HAND_R, lim);
        float m = amag(P_HAND_R);
        ok("挥手时右手摆开（" + r1(m) + "px，上限 " + r1(lim) + "px）", m > 8f);
        near("挥手不带动脸部", amag(P_FACE), 0f, 0.001f);
        near("挥手不带动头顶", amag(P_TOP), 0f, 0.001f);
        one(D_HAND_L, PartRig.driverLimit(D_HAND_L));
        float ml = amag(P_HAND_L);
        ok("左手同样能摆（" + r1(ml) + "px）", ml > 10f);
        near("左手场不碰右手", amag(P_HAND_R), 0f, 0.001f);
        one(D_ARM_R, PartRig.driverLimit(D_ARM_R));
        ok("手臂也能带动右手区域（" + r1(amag(P_HAND_R)) + "px）", amag(P_HAND_R) > 3f);
        near("手臂不带动脸部", amag(P_FACE), 0f, 0.001f);
    }

    static void wag() {
        System.out.println("[甩尾]");
        one(D_TAIL, 0f);
        near("不甩尾时尾梢不动", amag(P_TAIL), 0f, 0.001f);
        one(D_TAIL, PartRig.driverLimit(D_TAIL));
        float m = amag(P_TAIL);
        ok("甩尾时尾梢摆开（" + r1(m) + "px）", m > 12f);
        near("甩尾不带动脸部", amag(P_FACE), 0f, 0.001f);
        near("甩尾不带动头顶", amag(P_TOP), 0f, 0.001f);
        one(D_HAIR_R, PartRig.driverLimit(D_HAIR_R));
        ok("右长发也能摆（" + r1(amag(P_HAIR_R)) + "px）", amag(P_HAIR_R) > 6f);
        one(D_HAIR_L, PartRig.driverLimit(D_HAIR_L));
        ok("左长发也能摆（" + r1(amag(P_HAIR_L)) + "px）", amag(P_HAIR_L) > 6f);
        one(D_HAIR_F, PartRig.driverLimit(D_HAIR_F));
        ok("额发也能摆（" + r1(amag(P_HAIR_F)) + "px）", amag(P_HAIR_F) > 3f);
        // 额发探针原来写的是 P_FACE＝脸芯，那里按定义就没有发丝权重，
        // 「额发也能摆 0.0px」是探针指错了地方，不是额发不动。
    }

    static void headAndBreath() {
        System.out.println("[头与呼吸]");
        one(D_HEAD_DX, PartRig.driverLimit(D_HEAD_DX));
        ok("摇头时头顶跟着横移（" + r1(ax(P_TOP)) + "px）", ax(P_TOP) > 3f);
        near("摇头不带动胸腹", amag(P_BELLY), 0f, 0.001f);
        one(D_HEAD_DY, PartRig.driverLimit(D_HEAD_DY));
        ok("点头时头顶跟着下沉（" + r1(ay(P_TOP)) + "px）", ay(P_TOP) > 3f);
        one(D_HEAD_ROT, PartRig.driverLimit(D_HEAD_ROT));
        ok("歪头时头顶横移（" + r1(ax(P_TOP)) + "px）", Math.abs(ax(P_TOP)) > 3f);
        one(D_HEAD_ROT, 0f);
        near("不歪头时头顶不动", amag(P_TOP), 0f, 0.001f);
        // 呼吸：只压/撑躯干（绕 TORSO_PX/PY 缩放），头脸不动
        float b = PartRig.driverLimit(D_BREATH);
        one(D_BREATH, b);
        float belly = ay(P_BELLY);
        ok("呼吸时腰腹有竖向位移（" + r1(belly) + "px）", Math.abs(belly) > 1.5f);
        one(D_BREATH, -b);
        ok("反向呼吸位移反号（" + r1(ay(P_BELLY)) + "px）", ay(P_BELLY) * belly < 0f);
        near("呼吸不带动头顶", amag(P_TOP), 0f, 0.001f);
        near("呼吸不带动脸部", amag(P_FACE), 0f, 0.001f);
    }

    static void continuity() {
        System.out.println("[连续性]");
        // 判据用**真实动画帧**，不用「14 个驱动一起拉满」：
        // 动画层是把每个驱动各自钳在上限的（update() 末尾 clamp），
        // 「偶数驱动 +上限、奇数驱动 −上限」这种组合任何时刻都到不了 —— 拿它判折叠
        // 只会测出一个不存在的问题（旧判据实测最小格高 1.5px，报了但没人能触发）。
        // 真实帧同时覆盖了「待机 + 动作叠加」和「二阶跟随过冲」，才是能到达的最坏情况。
        float cw = W / MW, ch = H / MH;
        // 眼眶是唯一「本来就该塌」的地方：眨眼就是把眼睛纵向压到睑线，那几格必然趋近 0。
        // 判据要把眼球格摘掉，否则「眼睛眨得动」和「网格不折叠」两条自相矛盾。
        // 摘法用模型自己的眼权重场（W_EYE_L/R > 0.5），不手画方框。
        boolean[] eyeCell = new boolean[MW * MH];
        for (int j = 0; j < MH; j++) {
            for (int ii = 0; ii < MW; ii++) {
                float[] q = rig.weightsAt((ii + 0.5f) * cw, (j + 0.5f) * ch);
                eyeCell[j * MW + ii] = q[PartRig.W_EYE_L] + q[PartRig.W_EYE_R] > 0.5f;
            }
        }
        int eyeCells = 0;
        for (int k = 0; k < eyeCell.length; k++) if (eyeCell[k]) eyeCells++;
        float minCellX = 1e9f, maxCellX = 0f, minCellY = 1e9f, maxCellY = 0f;
        float eyeMinY = 1e9f;
        boolean finite = true;
        int worstFrame = 0;
        float worstMin = 1e9f;

        for (int i = 0; i < RigAnim.FRAMES; i++) {
            long t = RigAnim.now(i);
            rig.update(t, RigAnim.actionAt(t), RigAnim.progressAt(t), true);
            forced(rig.drivers().clone());
            for (int k = 0; k < verts.length; k++) {
                if (!isFinite(verts[k])) finite = false;
            }
            float fMinX = 1e9f, fMinY = 1e9f;
            for (int j = 0; j <= MH; j++) {
                for (int ii = 0; ii < MW; ii++) {
                    float step = gx(ii + 1, j) - gx(ii, j);
                    minCellX = Math.min(minCellX, step);
                    maxCellX = Math.max(maxCellX, step);
                    fMinX = Math.min(fMinX, step);
                }
            }
            for (int ii = 0; ii <= MW; ii++) {
                for (int j = 0; j < MH; j++) {
                    float step = gy(ii, j + 1) - gy(ii, j);
                    if (ii < MW && eyeCell[j * MW + ii]) {
                        eyeMinY = Math.min(eyeMinY, step);   // 眼球格允许塌到 0
                        continue;
                    }
                    minCellY = Math.min(minCellY, step);
                    maxCellY = Math.max(maxCellY, step);
                    fMinY = Math.min(fMinY, step);
                }
            }
            float fm = Math.min(fMinX / cw, fMinY / ch);
            if (fm < worstMin) { worstMin = fm; worstFrame = i; }

        }
        ok("真实动画帧网格不折叠（最小格宽 " + r1(minCellX) + "/" + r1(cw) + "px）",
           minCellX > cw * 0.15f);
        ok("真实动画帧网格不折叠（最小格高 " + r1(minCellY) + "/" + r1(ch) + "px，已摘掉 "
           + eyeCells + " 个眼球格）", minCellY > ch * 0.15f);
        ok("形变不夸张（最大格宽 " + r1(maxCellX) + " < 3×" + r1(cw) + "px）", maxCellX < cw * 3f);
        ok("形变不夸张（最大格高 " + r1(maxCellY) + " < 3×" + r1(ch) + "px）", maxCellY < ch * 3f);
        ok("真实动画帧顶点都是有限值（" + RigAnim.FRAMES + " 帧，最差第 " + worstFrame
           + " 帧 " + r3(worstMin) + "×）", finite);
        // 反向判据：眼球格必须真的被压塌过，否则「摘掉眼球格」就成了掩盖「眼睛根本不眨」
        ok("眼球格确实被压塌过（最小 " + r1(eyeMinY) + "px，眼睛真的闭上了）",
           eyeCells > 0 && eyeMinY < ch * 0.35f);
    }

    static void stability() {
        System.out.println("[稳定性]");
        boolean finite = true;
        float maxAbs = 0f, motion = 0f;
        PetAction[] all = PetAction.values();
        for (int f = 0; f < 300; f++) {
            long now = 1000L + f * 33L;
            PetAction a = all[f % all.length];
            float prog = (a == PetAction.IDLE) ? -1f : (f % 17) / 17f;
            frame(now, a, prog);
            for (int k = 0; k < verts.length; k++) {
                if (Float.isNaN(verts[k]) || Float.isInfinite(verts[k])) finite = false;
                maxAbs = Math.max(maxAbs, Math.abs(verts[k]));
            }
            motion += amag(P_TAIL) + amag(P_HAND_R);
        }
        ok("300 帧内顶点都是有限值", finite);
        ok("300 帧内顶点都没跑出画面（最远 " + Math.round(maxAbs) + "px）", maxAbs < W + 200f);
        ok("300 帧里确实在动（累计 " + Math.round(motion) + "px）", motion > 200f);
        rig.update(0L, PetAction.IDLE, -1f, false);
        rig.fill(0f, 0f, W, H, verts);
        ok("静止模式回到中性", rig.isNeutral());
        float maxOff = 0f;
        for (int j = 0; j <= MH; j++) {
            for (int i = 0; i <= MW; i++) {
                maxOff = Math.max(maxOff, Math.abs(dvx(i, j)) + Math.abs(dvy(i, j)));
            }
        }
        near("静止模式所有顶点回基准位", maxOff, 0f, 0.001f);
    }
}

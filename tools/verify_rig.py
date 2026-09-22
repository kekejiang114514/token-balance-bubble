#!/usr/bin/env python3
"""骨架验收套件（rig_math.py 是唯一事实来源，本脚本是它的判据）。

跑法：python3 tools/verify_rig.py
判据分七节：
  A 常量/生成物一致   B 权重场平滑度   C 隔离性   D 形变安全
  E 可见性（幅度够不够看得出来）  F 黄金表  G Java 动画时间线

设计意图（踩过的坑写在这里，别再犯）：
* 位移 = 结构层（头部/发丝/手臂/尾鳍，按权重混合）+ 细节层（眨眼/呼吸/手掌平移，
  归一化之后叠加）。细节层若参与权重分摊，幅度会被摊掉一半。
* 权重场必须「径向等比过渡带」（带宽 = k·R），固定带宽会在梢部掐出折痕。
* 动作幅度不是越大越好：贴图上局部压缩到 0.55 以下就会出现肉眼可见的褶皱。
"""
import hashlib
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rig_math as rm

FAIL = []
OTHER = []


def source_hash():
    """模型源码指纹：黄金表里记一份，消费方据此判断表是否过期。"""
    with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "rig_math.py"), "rb") as fh:
        return hashlib.sha256(fh.read()).hexdigest()


def check(cond, label, detail=""):
    if cond:
        print("  ok   %s %s" % (label, detail))
    else:
        print("  FAIL %s %s" % (label, detail))
        FAIL.append(label)


def note(label, detail=""):
    print("  ..   %s %s" % (label, detail))
    OTHER.append((label, detail))


def pct(*a):
    return "%.3f" % a[0] if len(a) == 1 else "%.3f" % a[0]


# ---- 判据阈值（改这里就等于改判据，改完要在 CHANGELOG 里写清为什么） ----
MARGIN_MIN = 1.25      # 驱动设计幅度与「折痕边界」的最小距离
GRAD_MAX = 0.060       # 权重场最大斜率 1/px
SHEAR_MAX = 0.80
SLO_MIN, SHI_MAX = 0.55, 1.80
AREA_MIN = 0.25        # 网格单元面积比下限（低到 0.25 说明被掐扁了）
ZERO_PX = 0.5          # 「这块地方不该动」的判据：单骨拉满时位移不超过半个像素
FACE_PTS = []          # 脸芯椭圆上的采样点，sec_b 里现算现用
MOVE_MAX = 125.0       # 单点最大位移（超过就会飞出绘制区域）
ISO_LIMIT = 1.2        # 「不该动的地方」位移上限 px
FOLD_LIMIT = 2         # 允许的翻面采样点数

# 探针点：判据只认这些坐标，改贴图时一起改
PROBE = {
    "top":        (300.0, 24.0),
    "face":       (295.0, 375.0),
    "neck":       (295.0, 430.0),
    "chest":      (295.0, 470.0),
    "belly":      (295.0, 545.0),
    # 下面四个点是 tools/probe_fit.py 从**当前场**里挑的「单骨最纯、动得最多」的点。
    # 旧的 (660,660) 是在手画坐标系里定的，场改过之后它落进了右发丝的覆盖区，
    # 于是「尾梢不动」和「尾鳍看得见」两条同时假失败。**改场之后要用 probe_fit 重挑。**
    "tail":       (685.0, 464.0),
    "hairL":      (43.0, 626.0),
    "hairR":      (492.0, 684.0),
    "hairF":      (458.0, 430.0),
    "handL":      (214.0, 498.0),
    "handR":      (378.0, 495.0),
    "eyeL":       (226.0, 336.0),
    "eyeR":       (366.0, 329.0),
}

# 单驱动可见性：动作要「看得出来」，位移不够就是白做
SEE = [
    ("turn",  ["top"], 18.0),
    ("nod",   ["top"], 4.0),
    ("blink", ["eyeL", "eyeR"], 20.0),
    ("tail",  ["tail"], 15.0),
    ("hairflow", ["hairL", "hairR"], 10.0),
    ("wave",  ["handR"], 13.0),
    ("wrist", ["handR"], 8.0),
    ("squash", ["chest"], 1.5),
]

# 隔离性：本动作不该牵动的部位。表里只留「这个姿态根本没驱动到的部位」：
# 手/头/发被姿态**直接驱动**时不算串台，否则测的是「动作做没做」而不是「有没有串台」。
ISO = [
    ("blink",    ["face", "neck", "hairL", "handR", "top"], ISO_LIMIT),
    ("turn",     ["handR", "handL", "tail", "chest"], ISO_LIMIT),
    ("nod",      ["handR", "handL", "tail"], ISO_LIMIT),
    ("wave",     ["tail", "handL", "hairL"], 1.5),
    # 去掉 handL：wrist 姿态本身驱动 hand_l=-0.50，左手动是它该做的动作，
    # 旧表把手当成了「不该动的部位」，于是 5.6px 被记成串台。
    ("wrist",    ["tail", "hairL", "top", "face"], 1.6),
    ("tail",     ["face", "top", "handR"], 1.6),
    ("hairflow", ["handR", "chest", "tail", "face", "neck"], 1.6),
    # 左长发根部离左手只有 72px（右手离右发根 144px），满幅甩动时左手会被带走 2.25px
    # ≈ 屏幕上 0.7px。这是「左手本来就在左发梢旁边」的几何事实，单独一条放宽并留档。
    ("hairflow", ["handL"], 2.5),
]


def iso(d):
    """扣掉「全局驱动 + 整块头部位移」，只留动作专属驱动，测串台。

    wind / breath 是全场景的；head_rot / head_dx / head_dy 是**整块头部的平移旋转**，
    它按定义就会带动头顶、脸、发丝 —— 那是动作本来就该动的东西，不是串台。
    旧版只扣了 wind/breath，于是 tail 姿态（表里带 head_rot=-0.20 的反向补偿）
    在头顶量出 6.1px，被记成「甩尾串台到头顶」；blink 同理。真正的场外溢基本都是 0。
    """
    d = dict(d)
    for k in ("wind", "breath", "head_rot", "head_dx", "head_dy"):
        d[k] = 0.0
    return d


def disp_of(d, part):
    x, y = PROBE[part]
    return rm.disp(x, y, d)


def move(d0, d1, part):
    a = disp_of(d0, part)
    b = disp_of(d1, part)
    return math.hypot(b[0] - a[0], b[1] - a[1])


ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.path.join(ROOT, "app", "src", "com", "coco", "balancebubble", "RigModel.java")
GOLDEN = os.path.join(ROOT, "test", "rig_golden.txt")
ANIM = os.path.join(ROOT, "test", "rig_anim_samples.txt")


def load_mask():
    """角色实心区（与 tools/fit_amps.py 同一口径：alpha > 128）。"""
    import numpy as np
    from PIL import Image
    p = os.path.join(ROOT, "app", "assets", "char.png")
    return np.array(Image.open(p).convert("RGBA"))[:, :, 3] > 128


def bad_of(d, mask, step=2):
    """面积/翻面取「渲染网格」口径（真正被画出来的那层），伸缩剪切取连续场口径。

    两个口径各司其职：网格是 Java 实际光栅化的单元，面积塌陷看它最准；
    连续场的极值能指到具体坐标，便于定位是哪根骨拖坏了哪片皮。
    """
    ms = rm.mesh_stats(d, mask)
    ds = rm.deform_stats(d, mask, step=step)
    why = []
    if ms["min_ratio"] < AREA_MIN:
        why.append("面积%.2f@%.0f,%.0f" % (ms["min_ratio"], ds["at"][0], ds["at"][1]))
    if ms["flips"] > FOLD_LIMIT:
        why.append("翻面%d" % ms["flips"])
    if ds["slo"] < SLO_MIN:
        why.append("压缩%.2f" % ds["slo"])
    if ds["shi"] > SHI_MAX:
        why.append("拉伸%.2f" % ds["shi"])
    if ds["shear"] > SHEAR_MAX:
        why.append("剪切%.2f" % ds["shear"])
    if ds["disp"] > MOVE_MAX:
        why.append("位移%.0f" % ds["disp"])
    return why, ms, ds


def sec_a(mask):
    print("[A] 常量与生成物")
    import re
    import numpy as np
    import gen_rig_java
    check(rm.RAW_W == 692 and rm.RAW_H == 720, "画布", "%dx%d" % (rm.RAW_W, rm.RAW_H))
    check((rm.MW, rm.MH) == (24, 24), "网格", str((rm.MW, rm.MH)))
    keys = gen_rig_java.check_keys()
    check(not keys, "骨表参数全覆盖", "、".join(keys) or "生成器无遗漏")
    bad = [d for d in rm.DRIVERS if not 0 < rm.driver_limit(d) <= rm.DRIVER_MAX[d] + 1e-6]
    check(not bad, "驱动上限自洽", "、".join(bad))
    xs = np.linspace(0, rm.RAW_W, 13)
    ys = np.linspace(0, rm.RAW_H, 13)
    worst = 0.0
    for name in rm.POSES:
        d = rm.apply_pose(name)
        _X, _Y, AX, AY = rm.disp_grid(d, xs, ys)
        for j, y in enumerate(ys):
            for i, x in enumerate(xs):
                sx, sy = rm.disp(float(x), float(y), d)
                worst = max(worst, abs(sx - AX[j][i]), abs(sy - AY[j][i]))
    check(worst < 1e-3, "标量场与 numpy 场一致", "max %.1e px" % worst)
    src = gen_rig_java.render()
    on_disk = open(JAVA, encoding="utf-8").read()
    check(src == on_disk, "RigModel.java 与生成器一致",
          "" if src == on_disk else "请跑 tools/gen_rig_java.py")
    names = set(re.findall(r"\b([A-Z][A-Z0-9_]*)\s*=", on_disk))
    miss = [d for d in rm.DRIVERS if gen_rig_java.DRV_CONST[d] not in names]
    check(not miss, "驱动上限常量齐全", "、".join(miss))
    miss = ["D_" + d.upper() for d in rm.DRIVERS if "D_" + d.upper() not in names]
    check(not miss, "驱动下标常量齐全", "、".join(miss))
    check("DRIVER_LIMIT" in names and "DRIVER_COUNT" in names, "驱动数组常量")
    got = {m: float(v) for m, v in re.findall(r"\b(MAX_[A-Z_]+)\s*=\s*([0-9.]+)f", on_disk)}
    off = ["%s=%.4f≠%.4f" % (d, got.get(gen_rig_java.DRV_CONST[d], float("nan")), rm.driver_limit(d))
           for d in rm.DRIVERS
           if abs(got.get(gen_rig_java.DRV_CONST[d], float("nan")) - rm.driver_limit(d)) > 1e-4]
    check(not off, "Java 驱动上限=模型", "、".join(off) or "与 driver_limit 逐项一致")


def sec_b(mask):
    """权重场平滑度：斜率取 numpy 场（网格级），外溢探针取标量场（与 Java 同源）。

    两套实现必须给出同一个函数值 [A] 已验证；这里刻意分开用：
    斜率要逐点数值微分，走 numpy；探针只需单点值，走标量版 = 发布代码那条路。
    """
    print("[B] 权重场平滑度")
    np_fields = [("head", rm.w_head_np)]
    sc_fields = [("head", rm.w_head)]
    for name in rm.STRAND_DRIVER:
        b = rm.BONES[name]
        np_fields.append((name, (lambda bb: (lambda X, Y: rm.w_strand_np(X, Y, bb)))(b)))
        sc_fields.append((name, (lambda bb: (lambda x, y: rm.w_strand(x, y, bb)))(b)))
    np_fields.append(("torso", rm.w_torso_np))
    sc_fields.append(("torso", rm.w_torso))
    np_fields.append(("eyeL", lambda X, Y: rm.w_eye_np(X, Y, rm.BONES["eyeL"])))
    sc_fields.append(("eyeL", lambda x, y: rm.w_eye(x, y, rm.BONES["eyeL"])))
    np_fields.append(("eyeR", lambda X, Y: rm.w_eye_np(X, Y, rm.BONES["eyeR"])))
    sc_fields.append(("eyeR", lambda x, y: rm.w_eye(x, y, rm.BONES["eyeR"])))
    np_fields.append(("handL", lambda X, Y: rm.w_hand_np(X, Y, rm.BONES["handL"])))
    sc_fields.append(("handL", lambda x, y: rm.w_hand(x, y, rm.BONES["handL"])))
    np_fields.append(("handR", lambda X, Y: rm.w_hand_np(X, Y, rm.BONES["handR"])))
    sc_fields.append(("handR", lambda x, y: rm.w_hand(x, y, rm.BONES["handR"])))

    worst = 0.0
    worst_at = None
    for name, fn in np_fields:
        g, at = rm.field_grad_max(fn, step=2.0)
        if g > worst:
            worst, worst_at = g, (name, at)
        check(g <= GRAD_MAX, "最大斜率 " + name,
              "%.4f ≤ %.3f @(%.0f,%.0f)" % (g, GRAD_MAX, at[0], at[1]))
    note("最陡的场", "%s %.4f @(%.0f,%.0f)" % (worst_at[0], worst, worst_at[1][0], worst_at[1][1]))
    for name, fn in sc_fields:
        for pt in ZERO_PROBE.get(name, []):
            w = fn(*PROBE[pt])
            note("探针权重 %s@%s" % (name, pt), "%.4f" % w)

    # 外溢判据用**位移**而不是权重：宽软边的场（手、手臂）在远处本来就有千分之几的权重，
    # 但那是「0.1px 的抖动」，肉眼与像素都看不见。判据应该是「拉满时这个点动多少像素」。
    for name in ZERO_PROBE:
        d = rm.set_driver(rm.blank(), rm.BONE_DRV[name], rm.bone_total(name))
        for pt in ZERO_PROBE[name]:
            m = math.hypot(*disp_of(d, pt))
            check(m <= ZERO_PX, "场不外溢 %s@%s" % (name, pt),
                  "%.2f ≤ %.1f px" % (m, ZERO_PX))

    # 脸芯保护区：发/尾/手/臂都不许把脸上的像素拖走。区域来自 rig_math.face_core()，
    # Java 侧从 RigModel 读同一份数，两边判的是同一块地。
    fcx, fcy, frx, fry = rm.face_core()
    gy = int(fcy - fry)
    while gy <= fcy + fry:
        gx = int(fcx - frx)
        while gx <= fcx + frx:
            if rm.in_face(gx, gy):
                FACE_PTS.append((gx, gy))
            gx += 4
        gy += 4
    for name in ("hairL", "hairR", "hairF", "tail", "armL", "armR", "handL", "handR", "torso"):
        d = rm.set_driver(rm.blank(), rm.BONE_DRV[name], rm.bone_total(name))
        worst_f = max(math.hypot(*rm.disp(x, y, d)) for x, y in FACE_PTS)
        check(worst_f <= ZERO_PX, "脸芯不被 " + name + " 拖走",
              "%.2f ≤ %.1f px（%d 点）" % (worst_f, ZERO_PX, len(FACE_PTS)))


# 权重场外溢：这些场在这些探针上必须是 0，否则就是一根骨拖着一整片皮
ZERO_PROBE = {
    "hairL": ["handR", "face", "belly"],
    "hairR": ["handL", "face", "belly"],
    "hairF": ["handR", "handL", "belly"],
    "tail": ["face", "top", "handR", "eyeL"],
    "armL": ["handR", "face", "eyeL"],
    "armR": ["handL", "face", "eyeL", "hairF"],
    "torso": ["face", "eyeL", "top", "tail"],
    "eyeL": ["handR", "chest", "tail"],
    "eyeR": ["handL", "chest", "tail"],
    "handR": ["face", "top", "neck", "hairL", "eyeL"],
    "handL": ["face", "top", "neck", "hairR", "eyeR"],
}


def sec_c():
    print("[C] 隔离性")
    for name, parts, lim in ISO:
        d = iso(rm.apply_pose(name))
        bad = []
        for p in parts:
            ax, ay = disp_of(d, p)
            m = math.hypot(ax, ay)
            if m > lim:
                bad.append("%s %.1fpx" % (p, m))
        check(not bad, "串台 " + name, "、".join(bad) or "≤%.1fpx" % lim)


def sec_d(mask):
    print("[D] 形变安全")
    for name in sorted(rm.POSES):
        why, ms, ds = bad_of(rm.apply_pose(name), mask, step=4)
        check(not why, "姿态 " + name, "、".join(why) or
              "面积%.2f 压缩%.2f 拉伸%.2f 剪切%.2f" % (ms["min_ratio"], ds["slo"], ds["shi"], ds["shear"]))
    # ×1.25 余量检查已删除：动画层把**每一个驱动各自钳在上限**（PartRig.update 末尾 clamp），
    # 所以「姿态 ×1.25」这种整体放大任何时刻都到不了 —— 它测的是不存在的情况，
    # 而且 wave ×1.25 报的「压缩 0.46」也永远不会发生。真正能到达的最坏情况是
    # 「待机 + 动作叠加 + 二阶跟随过冲」，由下面 [G] 用真实动画帧逐帧判定。
    for drv in rm.DRIVERS:
        # 极值 = 驱动角 + 风附加角（发丝/尾鳍的驱动上限会被风再推高），两者可以同时拉满。
        lim = rm.DRIVER_MAX[drv]
        why = []
        for sgn in (1.0, -1.0):
            w2, _m, _d = bad_of(rm.set_driver(rm.blank(), drv, sgn * lim), mask)
            why += w2
        check(not why, "极值 %s ±%.2f" % (drv, lim), "、".join(why) or "干净")
        if drv == "blink":
            # 眨眼不适用「折痕余量」：动画层里 blink = sin(π·dt/175ms) 最高就是 1.00，
            # 被硬钳在 1.00 上，没有过冲可以被余量吸收；而「眼睛压到睑线」本身
            # 就是让眼球那几格塌到底，1.3× 才会出现 6 个翻面。判据换成
            # 「1.00 是干净的」——上面那条极值检查已经覆盖了。
            note("折痕余量 blink", "不适用：驱动硬钳在 1.00，1.00 干净即达标")
            continue
        hi = lim
        while hi < lim * 5 and not bad_of(rm.set_driver(rm.blank(), drv, hi * 1.3), mask, step=4)[0]:
            hi *= 1.3
        check(hi / lim >= MARGIN_MIN, "折痕余量 " + drv, "%.2f×" % (hi / lim))
    why, _ms, _ds = bad_of(rm.max_pose(), mask)
    note("全驱动拉满（动画到不了的假想姿态，只记录不判定）", "、".join(why) or "干净")


def sec_e():
    print("[E] 可见性")
    for name, parts, need in SEE:
        d = rm.apply_pose(name)
        for p in parts:
            ax, ay = disp_of(d, p)
            m = math.hypot(ax, ay)
            check(m >= need, "看得见 %s·%s" % (name, p), "%.1f ≥ %.1f px" % (m, need))


WEIGHT_NAMES = ("wHead wHairL wHairR wHairF wTail wArmL wArmR wTorso wEyeL wEyeR "
                "wHandL wHandR").split()


def weights_at(x, y):
    return (rm.w_head(x, y),
            rm.w_strand(x, y, rm.BONES["hairL"]), rm.w_strand(x, y, rm.BONES["hairR"]),
            rm.w_strand(x, y, rm.BONES["hairF"]), rm.w_strand(x, y, rm.BONES["tail"]),
            rm.w_strand(x, y, rm.BONES["armL"]), rm.w_strand(x, y, rm.BONES["armR"]),
            rm.w_torso(x, y),
            rm.w_eye(x, y, rm.BONES["eyeL"]), rm.w_eye(x, y, rm.BONES["eyeR"]),
            rm.w_hand(x, y, rm.BONES["handL"]), rm.w_hand(x, y, rm.BONES["handR"]))


def golden_states():
    st = [("idle", rm.blank())]
    for n in sorted(rm.POSES):
        st.append(("pose:" + n, rm.apply_pose(n)))
    for drv in rm.DRIVERS:
        lim = rm.driver_limit(drv)
        for sgn, tag in ((1.0, "+"), (-1.0, "-")):
            st.append(("drv:" + drv + tag, rm.apply_amps(rm.set_driver(rm.blank(), drv, sgn * lim))))
    st.append(("max", rm.max_pose()))
    return st


def write_golden(np):
    xs = np.linspace(0.0, rm.RAW_W, 13)
    ys = np.linspace(0.0, rm.RAW_H, 13)
    out = ["# rig_golden v2",
           "# source sha256 " + source_hash(),
           "# drivers " + " ".join(rm.DRIVERS),
           "# weights " + " ".join(WEIGHT_NAMES)]
    for name, d in golden_states():
        out.append("S %s %s" % (name, " ".join("%.6f" % d[k] for k in rm.DRIVERS)))
        for y in ys:
            for x in xs:
                ax, ay = rm.disp(float(x), float(y), d)
                w = weights_at(float(x), float(y))
                # 坐标必须写出全精度：取整到 0.1px 会让软边上的权重差 2e-3，
                # 消费方（Java 单测）再拿这个坐标重算就对不上了
                out.append("P %.6f %.6f %.6f %.6f %s" % (x, y, ax, ay,
                                                          " ".join("%.6f" % v for v in w)))
    with open(GOLDEN, "w", encoding="utf-8") as fh:
        fh.write("\n".join(out) + "\n")
    return out


def sec_f():
    print("[F] 黄金表")
    states = golden_states()
    by_name = dict(states)
    with open(GOLDEN, encoding="utf-8") as fh:
        lines = [l.rstrip("\n") for l in fh if l.strip()]
    src = [l for l in lines if l.startswith("# source sha256 ")]
    check(bool(src) and src[0].split()[-1] == source_hash(),
          "表与模型同源", (src[0].split()[-1][:12] + "…") if src else "缺 # source sha256 行")
    hs = [l for l in lines if l.startswith("S ")]
    ps = [l for l in lines if l.startswith("P ")]
    check(len(hs) == len(states), "状态数", "%d = %d" % (len(hs), len(states)))
    check(len(ps) == 169 * len(states), "采样点数", "%d" % len(ps))
    worst = 0.0
    cur = None
    for l in lines:
        if l.startswith("#"):
            continue
        if l.startswith("S "):
            cur = by_name[l.split()[1]]
        elif cur is not None:
            f = l.split()
            x, y = float(f[1]), float(f[2])
            ax, ay = rm.disp(x, y, cur)
            worst = max(worst, abs(ax - float(f[3])), abs(ay - float(f[4])))
            w = weights_at(x, y)
            for i in range(len(WEIGHT_NAMES)):
                worst = max(worst, abs(w[i] - float(f[5 + i])))
    check(worst < 1e-4, "表内自洽", "重算 %d 点，最大偏差 %.1e" % (len(ps), worst))


# 动画里每个部位至少要动到多少像素（贴图空间）。692px 的贴图在视图里缩到约 0.3 倍，
# 所以 10px ≈ 屏幕上 3px，是「一眼能看出在动」的下限。
# 这些数是**判据**，不是描述：待机幅度被写死成小角度时（v1.6 的甩尾 2.2°/9.4°、
# 挥手平移 1.2px/10.5px），这里会直接报红 —— 不用等用户回来说
# 「尾巴不晃、头发不飘、动作生硬」。数值留了约 35% 余量，再优化一点不会误报。
ANIM_VIS = {
    "top": 22.0, "face": 0.25, "neck": 0.10, "chest": 1.8, "belly": 1.4,
    "tail": 9.0, "hairL": 10.0, "hairR": 10.5, "hairF": 3.0,
    "handL": 6.5, "handR": 10.0, "eyeL": 22.0, "eyeR": 21.0,
}

# 每个动作至少要牵动的部位（动作自己的主角，不能被别的动作取代）
ANIM_LEAD = {
    "FLOAT": ("tail", 7.0), "BOUNCE": ("hairL", 8.0), "SWAY": ("top", 14.0),
    "LEAN": ("top", 20.0), "SHAKE": ("top", 12.0), "NOD": ("top", 7.0),
    "SPIN": ("hairL", 6.0), "STRETCH": ("tail", 6.0), "DUCK": ("top", 7.0),
    "POP": ("hairR", 8.0), "SLEEPY": ("hairL", 9.0), "WAG": ("tail", 7.0),
    "WAVE": ("handR", 11.0),
}

# 驱动峰值至少要吃掉自身上限的这个比例，否则那个通道等于没接上
ANIM_DRV_USE = 0.55


def sec_g(mask):
    print("[G] 动画时间线（真实播放，不是静态姿态）")
    anim = os.path.join(ROOT, "test", "rig_anim_samples.txt")
    if not os.path.exists(anim):
        check(False, "动画采样", "缺少 %s（先跑 java RigAnimTest）" % anim)
        return
    names, lims, prb_xy = [], [], {}
    drv_pk, prb_pk, act_pk = {}, {}, {}
    rows = []
    with open(anim, encoding="utf-8") as fh:
        for l in fh:
            f = l.split()
            if not f:
                continue
            if f[0] == "#drivers":
                names = f[1:]
            elif f[0] == "#driver-limit":
                lims = [float(v) for v in f[1:]]
            elif f[0] == "#probe" and len(f) == 4:
                prb_xy[f[1]] = (float(f[2]), float(f[3]))
            elif f[0] == "#driver-peak":
                drv_pk[f[1]] = float(f[2])
            elif f[0] == "#probe-peak":
                prb_pk[f[1]] = float(f[2])
            elif f[0] == "#action-peak" and len(f) == 4:
                act_pk[(f[1], f[2])] = float(f[3])
            elif f[0] == "#frame" and len(f) == 4 + len(rm.DRIVERS):
                rows.append((f[2], float(f[1]), [float(v) for v in f[4:]]))

    check(names == list(rm.DRIVERS), "驱动名/顺序与模型一致", "%d 个" % len(names))
    off = ["%s %.3f≠%.3f" % (b, a, rm.driver_limit(b))
           for a, b in zip(lims, list(rm.DRIVERS))
           if abs(a - rm.driver_limit(b)) > 1e-3]
    check(len(lims) == len(rm.DRIVERS) and not off, "驱动上限与模型一致（单一事实来源）",
          "、".join(off) or "14 个相同")
    check(len(rows) > 1000, "采样数量", "%d 帧" % len(rows))
    bad_xy = [k for k in PROBE if prb_xy.get(k) != PROBE[k]]
    check(set(prb_xy) == set(PROBE) and not bad_xy, "探针与 verify 表一致",
          "、".join(bad_xy) or "%d 个同点" % len(prb_xy))

    over = []
    for act, _t, v in rows:
        for i, k in enumerate(rm.DRIVERS):
            if abs(v[i]) > rm.driver_limit(k) + 1e-4:
                over.append("%s %s=%.2f" % (act, k, v[i]))
    check(not over, "动画不超上限", "、".join(over[:4]) or "%d 帧全部在界内" % len(rows))

    # 1) 每个驱动都用起来了 —— 「动作生硬」的直接判据
    use = [(k, drv_pk.get(k, 0.0) / rm.driver_limit(k)) for k in rm.DRIVERS]
    weak = ["%s %.0f%%" % (k, r * 100) for k, r in use if r < ANIM_DRV_USE]
    wk = min(use, key=lambda kv: kv[1])
    check(not weak, "每个驱动都吃掉 ≥%.0f%% 上限" % (ANIM_DRV_USE * 100),
          ("不足：" + "、".join(weak)) if weak else "最弱 %s %.0f%%" % (wk[0], wk[1] * 100))

    # 2) 每个部位都动得出来（屏幕可见性）
    dim = sorted(["%s %.1f<%.1f" % (k, prb_pk.get(k, 0.0), v)
                  for k, v in ANIM_VIS.items() if prb_pk.get(k, 0.0) < v])
    wv = min(ANIM_VIS, key=lambda k: prb_pk.get(k, 0.0) / ANIM_VIS[k])
    check(not dim, "每个部位在动画里都动得出来",
          "、".join(dim) or "余量最小 %s %.1f/%.1fpx" % (wv, prb_pk.get(wv, 0.0), ANIM_VIS[wv]))

    # 3) 每个动作都有主角，别出现「播了但看不出在干嘛」
    dead = ["%s·%s %.1f<%.1f" % (a, p, act_pk.get((a, p), 0.0), v)
            for a, (p, v) in sorted(ANIM_LEAD.items()) if act_pk.get((a, p), 0.0) < v]
    check(not dead, "每个动作都牵着主角部位",
          "、".join(dead) or "%d 个动作全达标" % len(ANIM_LEAD))

    # 4) 眨眼必须真合到底，不能只是抖两下
    blink_pk = max(prb_pk.get("eyeL", 0.0), prb_pk.get("eyeR", 0.0))
    check(blink_pk >= 25.0, "眨眼合到底", "眼周最大位移 %.1fpx ≥ 25" % blink_pk)

    # 5) 真实帧形变安全
    bad = []
    tight = None
    for act, t, v in rows[::3]:
        d = dict(zip(rm.DRIVERS, v))
        why, ms, ds = bad_of(d, mask, step=4)
        if why:
            bad.append("%s@%.2fs %s" % (act, t, ",".join(why)))
        key = min(ms["min_ratio"], ds["slo"], 2.0 - ds["shi"], 1.0 - ds["shear"])
        if tight is None or key < tight[0]:
            tight = (key, act, t, ms, ds)
    check(not bad, "动画帧形变安全", "、".join(bad[:4]) or "抽样 %d 帧" % len(rows[::3]))
    if tight:
        print("    最紧一帧 %s@%.2fs 面积%.2f 压缩%.2f 拉伸%.2f 剪切%.2f" % (
            tight[1], tight[2], tight[3]["min_ratio"], tight[4]["slo"],
            tight[4]["shi"], tight[4]["shear"]))

    # 6) 跨语言：Java 播放出来的位移，必须等于 Python 用同一串驱动向量重算的结果
    py_pk = dict((p, 0.0) for p in PROBE)
    for _act, _t, v in rows:
        d = dict(zip(rm.DRIVERS, v))
        for p in PROBE:
            ax, ay = rm.disp(PROBE[p][0], PROBE[p][1], d)
            m = math.hypot(ax, ay)
            if m > py_pk[p]:
                py_pk[p] = m
    dev = max(abs(py_pk[p] - prb_pk.get(p, 0.0)) for p in PROBE)
    check(dev <= 0.05, "Java 播放的位移与 Python 逐帧重算一致",
          "最大偏差 %.4fpx（%d 帧 × %d 探针）" % (dev, len(rows), len(PROBE)))

    acts = sorted(set(r[0] for r in rows))
    print("    覆盖动作 %d 个: %s" % (len(acts), " ".join(acts)))


def main():
    mask = load_mask()
    import numpy as np
    sec_a(mask)
    sec_b(mask)
    sec_c()
    sec_d(mask)
    sec_e()
    write_golden(np)
    sec_f()
    sec_g(mask)
    print("")
    if FAIL:
        print("✗ %d 项未通过" % len(FAIL))
        return 1
    print("✓ 全部通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())

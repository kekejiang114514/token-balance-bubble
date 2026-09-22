# -*- coding: utf-8 -*-
"""把 tools/rig_math.py 的骨表导出成 Java 常量（app/src/.../RigModel.java）。

为什么要有这个生成器：
    骨表、权重场参数、幅度上限原本在 Python 与 Java 里各写一份，改一边忘一边就会
    「模型对了、APK 里没变」。现在 Java 侧的常量**全部**由这里产出，
    tools/verify_rig.py 第 A 节会断言磁盘上的 RigModel.java 与生成器输出逐字节一致。

用法：python3 tools/gen_rig_java.py [--check]
        --check 只比对不写盘（CI/自检用）
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)
import rig_math as rm  # noqa: E402

OUT = os.path.join(ROOT, "app/src/com/coco/balancebubble/RigModel.java")

# 骨名 -> Java 常量前缀
PREFIX = {"head": "HEAD", "hairL": "HAIR_L", "hairR": "HAIR_R", "hairF": "HAIR_F",
          "tail": "TAIL", "armL": "ARM_L", "armR": "ARM_R",
          "handL": "HAND_L", "handR": "HAND_R", "torso": "TORSO",
          "eyeL": "EYE_L", "eyeR": "EYE_R"}

# 驱动名 -> Java 常量名（动画层裁剪用）
DRV_CONST = {"head_rot": "MAX_HEAD_ROT", "head_dx": "MAX_HEAD_DX", "head_dy": "MAX_HEAD_DY",
             "hair_l": "MAX_HAIR_L", "hair_r": "MAX_HAIR_R", "hair_f": "MAX_HAIR_F",
             "tail": "MAX_TAIL", "arm_l": "MAX_ARM_L", "arm_r": "MAX_ARM_R",
             "hand_l": "MAX_HAND_L", "hand_r": "MAX_HAND_R", "breath": "MAX_BREATH",
             "blink": "MAX_BLINK", "wind": "MAX_WIND"}


def f(v):
    return "%.4ff" % float(v)


# 各 kind 的骨参数键；多出来的键说明生成器还没覆盖，A 节会报出来
BONE_KEYS = {
    "head": {"kind", "pivot", "amp", "win", "fade"},
    "rot": {"kind", "pivot", "amp", "wind", "k", "ts", "axis", "r"},
    "trans": {"kind", "c", "amp", "r", "k", "dir"},
    "scale": {"kind", "pivot", "amp", "squeeze", "win", "fade"},
    "blink": {"kind", "pivot", "ell", "close_y", "amp"},
}


def check_keys():
    """返回「骨表里存在但生成器不认识的参数」，空列表表示全覆盖。"""
    bad = []
    for name, b in rm.BONES.items():
        known = BONE_KEYS.get(name) or BONE_KEYS[b["kind"]]
        extra = set(b) - known
        if extra:
            bad.append("%s: %s" % (name, "、".join(sorted(extra))))
    return bad


def MESH():
    return (rm.MW, rm.MH)


def render():
    return emit()


def emit():
    L = []
    L.append("package com.coco.balancebubble;")
    L.append("")
    L.append("/**")
    L.append(" * 骨架模型常量。**本文件由 tools/gen_rig_java.py 从 tools/rig_math.py 生成，请勿手改。**")
    L.append(" *")
    L.append(" * 坐标一律是贴图像素（%d×%d）。骨表、权重场、幅度上限的唯一事实来源是" % (rm.RAW_W, rm.RAW_H))
    L.append(" * <code>tools/rig_math.py</code>；改模型请改那里，然后跑")
    L.append(" * <code>python3 tools/verify_rig.py</code> 验证、<code>python3 tools/gen_rig_java.py</code> 落盘。")
    L.append(" *")
    L.append(" * 幅度上限由 tools/fit_amps.py 二分标定（判据：网格无折叠、单元面积≥0.55、")
    L.append(" * 主伸缩∈[0.55,1.80]、剪切≤0.80、位移≤125px，眼区不计）。")
    L.append(" */")
    L.append("final class RigModel {")
    L.append("    private RigModel() {}")
    L.append("")
    L.append("    // ---- 画布与网格 ----")
    L.append("    static final float RAW_W = %s, RAW_H = %s;" % (f(rm.RAW_W), f(rm.RAW_H)))
    L.append("    static final int MESH_W = %d, MESH_H = %d;" % (rm.MW, rm.MH))
    L.append("")

    # 头
    b = rm.BONES["head"]
    cx, full, zero = b["win"]
    y_full, y_fade = b["fade"]
    px, py = b["pivot"]
    L.append("    // ---- 头：绕颈点旋转（歪头）+ 摇头/点头平移 ----")
    L.append("    static final float HEAD_PX = %s, HEAD_PY = %s;" % (f(px), f(py)))
    L.append("    static final float HEAD_AMP = %s;" % f(b["amp"]))
    L.append("    static final float HEAD_WIN_CX = %s, HEAD_WIN_FULL = %s, HEAD_WIN_ZERO = %s;"
             % (f(cx), f(full), f(zero)))
    L.append("    static final float HEAD_WIN_Y = %s, HEAD_WIN_FADE = %s;" % (f(y_full), f(y_fade)))
    L.append("")

    # 发丝/手臂（rot + 骨段）
    order = [("hairL", "长发（左）"), ("hairR", "长发（右）"), ("hairF", "刘海"),
             ("tail", "尾鳍"), ("armL", "手臂（左）"), ("armR", "手臂（右）")]
    for name, cn in order:
        b = rm.BONES[name]
        p = PREFIX[name]
        x0, y0, x1, y1 = b["axis"]
        r0, r1 = b["r"]
        px, py = b["pivot"]
        L.append("    // ---- %s：绕支点旋转，权重沿骨段向梢部递增 ----" % cn)
        L.append("    static final float %s_PX = %s, %s_PY = %s;" % (p, f(px), p, f(py)))
        L.append("    static final float %s_AMP = %s;" % (p, f(b["amp"])))
        if "wind" in b:
            L.append("    static final float %s_WIND = %s;   // 风对旋转角的附加增益（度）"
                     % (p, f(b["wind"])))
        L.append("    static final float %s_X0 = %s, %s_Y0 = %s, %s_X1 = %s, %s_Y1 = %s;"
                 % (p, f(x0), p, f(y0), p, f(x1), p, f(y1)))
        L.append("    static final float %s_R0 = %s, %s_R1 = %s;" % (p, f(r0), p, f(r1)))
        L.append("    static final float %s_K = %s, %s_TS = %s;" % (p, f(b["k"]), p, f(b.get("ts", 1.0))))
        L.append("")

    # 手掌平移
    for name, cn in (("handL", "手掌（左）"), ("handR", "手掌（右）")):
        b = rm.BONES[name]
        p = PREFIX[name]
        cx_, cy_ = b["c"]
        dx_, dy_ = b["dir"]
        L.append("    // ---- %s：挥手主体是刚体平移，不是旋转 ----" % cn)
        L.append("    static final float %s_CX = %s, %s_CY = %s;" % (p, f(cx_), p, f(cy_)))
        L.append("    static final float %s_R = %s, %s_K = %s;" % (p, f(b["r"]), p, f(b["k"])))
        L.append("    static final float %s_DIRX = %s, %s_DIRY = %s;" % (p, f(dx_), p, f(dy_)))
        L.append("    static final float %s_AMP = %s;" % (p, f(b["amp"])))
        L.append("")

    # 躯干
    b = rm.BONES["torso"]
    p = "TORSO"
    cx, full, zero = b["win"]
    y0, y1, y_fade = b["fade"]
    px, py = b["pivot"]
    L.append("    // ---- 躯干：呼吸（纵向缩放，横向压 squeeze 倍） ----")
    L.append("    static final float TORSO_PX = %s, TORSO_PY = %s;" % (f(px), f(py)))
    L.append("    static final float TORSO_AMP = %s, TORSO_SQUEEZE = %s;"
             % (f(b["amp"]), f(b["squeeze"])))
    L.append("    static final float TORSO_WIN_CX = %s, TORSO_WIN_FULL = %s, TORSO_WIN_ZERO = %s;"
             % (f(cx), f(full), f(zero)))
    L.append("    static final float TORSO_FADE_Y0 = %s, TORSO_FADE_Y1 = %s, TORSO_FADE = %s;"
             % (f(y0), f(y1), f(y_fade)))
    L.append("")

    # 眼睛
    for name, cn in (("eyeL", "左眼"), ("eyeR", "右眼")):
        b = rm.BONES[name]
        p = PREFIX[name]
        cx_, cy_, rx_, ry_, soft = b["ell"]
        L.append("    // ---- %s：眨眼时整块压到睑线 ----" % cn)
        L.append("    static final float %s_CX = %s, %s_CY = %s;" % (p, f(cx_), p, f(cy_)))
        L.append("    static final float %s_RX = %s, %s_RY = %s, %s_SOFT = %s;"
                 % (p, f(rx_), p, f(ry_), p, f(soft)))
        L.append("    static final float %s_CLOSE_Y = %s, %s_AMP = %s;"
                 % (p, f(b["close_y"]), p, f(b["amp"])))
        L.append("")

    # 脸芯保护区
    L.append("    // ---- 脸芯保护区（椭圆 cx,cy,rx,ry 按 shrink 收缩）----")
    L.append("    // 发丝/尾巴/手/手臂都不许把这块地的像素拖走。定义来自 rig_math.FACE，")
    L.append("    // 不是手画的方框：旧版 Java 单测自己写了 x∈[240,430] y∈[340,560] 的矩形，")
    L.append("    // 场改过之后那个矩形一半落在脸外，于是「脸被发丝带走 N 处」全是假失败。")
    L.append("    static final float FACE_CX = %s, FACE_CY = %s;" % (f(rm.FACE["cx"]), f(rm.FACE["cy"])))
    L.append("    static final float FACE_RX = %s, FACE_RY = %s, FACE_SHRINK = %s;"
             % (f(rm.FACE["rx"]), f(rm.FACE["ry"]), f(rm.FACE["shrink"])))
    L.append("")

    # 驱动上限
    L.append("    // ---- 驱动上限（动画层每帧按这个裁剪，保证永远落在已验证的幅度内） ----")
    L.append("    // 发丝/尾鳍是「驱动角 + wind×骨风增益」，所以驱动本身只能用到骨幅度（amp），")
    L.append("    // 上限值 = amp + wind×MAX_WIND 由 verify_rig 第 A 节核对。")
    line = []
    for drv in rm.DRIVERS:
        line.append("    static final float %s = %s;" % (DRV_CONST[drv], f(rm.driver_limit(drv))))
    L.extend(line)
    L.append("")
    L.append("    // ---- 驱动下标（驱动数组的顺序，动画层与自检共用） ----")
    L.append("    static final int D_HEAD_ROT = 0, D_HEAD_DX = 1, D_HEAD_DY = 2;")
    L.append("    static final int D_HAIR_L = 3, D_HAIR_R = 4, D_HAIR_F = 5, D_TAIL = 6;")
    L.append("    static final int D_ARM_L = 7, D_ARM_R = 8;")
    L.append("    static final int D_HAND_L = 9, D_HAND_R = 10;")
    L.append("    static final int D_BREATH = 11, D_BLINK = 12, D_WIND = 13;")
    L.append("    static final int DRIVER_COUNT = %d;" % len(rm.DRIVERS))
    L.append("")
    L.append("    /** 驱动名的显示顺序，自检报告用。 */")
    L.append("    static final String[] DRIVER_NAMES = {")
    L.append("        " + ", ".join('"%s"' % d for d in rm.DRIVERS))
    L.append("    };")
    L.append("")
    L.append("    /** 各驱动的裁剪上限（绝对值）。 */")
    L.append("    static final float[] DRIVER_LIMIT = {")
    L.append("        " + ", ".join(DRV_CONST[d] for d in rm.DRIVERS))
    L.append("    };")
    L.append("}")
    return "\n".join(L) + "\n"


def main():
    src = emit()
    check = "--check" in sys.argv
    old = None
    if os.path.exists(OUT):
        with open(OUT, encoding="utf-8") as fh:
            old = fh.read()
    if check:
        if old != src:
            print("RigModel.java 与生成器输出不一致（跑 python3 tools/gen_rig_java.py 重新生成）")
            sys.exit(1)
        print("RigModel.java 与生成器输出一致")
        return
    with open(OUT, "w", encoding="utf-8") as fh:
        fh.write(src)
    print("已写入 %s（%d 字节，%d 行）" % (os.path.relpath(OUT, ROOT), len(src.encode("utf-8")),
                                          src.count("\n")))


if __name__ == "__main__":
    main()

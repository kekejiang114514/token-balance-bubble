# -*- coding: utf-8 -*-
"""骨架位移场：本项目角色几何与动作幅度的**单一事实来源**。

坐标一律是贴图像素（692×720）。骨表参数全部由立绘实测标定
（`/tmp/measure.py` 的色块/连通域结果：脸 x178-414 y250-430、左眼 x203-251 y318-377、
右眼 x337-390 y307-368、左手 x196-233 y479-518、右手 x360-397 y476-516、
左长发带 x30-210 y400-660、右长发带 x390-590 y420-660、右侧尾鳍 x590-692 y455-600）。

骨架（每根骨 = 支点 + 权重场 + 若干自由度）：

  head    绕颈点 (300,442) 旋转，另配摇头 dx / 点头 dy 两个平移自由度
  hairL/R/F  绕各自发根旋转，权重沿发丝**向发梢递增** —— 越靠梢越灵，这是「头发会飘」的来源
  armL/R  绕肩点旋转（自然摆臂；旋转角受折痕限制，只有 ~9°）
  handL/R 手掌平移（挥手的主体：肩关节转不动，手掌得靠平移才看得见）
  tail    右侧尾鳍绕根部旋转（甩尾）
  torso   呼吸：绕腰部的纵向缩放（横向压 0.45 倍，像胸腔起伏）
  eyeL/R  竖向压扁到**睑线**（眨眼）

两类贡献分开处理，这是踩过坑之后的结论：

* **结构层**（head / hair / arm / tail）：位移按 Σwᵢ·Δᵢ / max(1, Σwᵢ) 混合，
  权重场重叠处自然过渡。权重场重叠会把各自的幅度摊薄，所以手/脸附近的场要收紧，
  避免「挥手只挥一半」。
* **细节层**（眨眼、呼吸、手掌平移）：**在归一化之后叠加**，不参与权重分摊。
  否则眼睛落在头部权重里（w=1），眨眼只剩一半幅度；手掌也会被手臂权重摊薄。

为什么坚持「整图连续变形」而不是拆图层：
单张平面立绘拆部件，关节一转就会在接缝处裂开/露底。这一点是实测过的
（见 tools/model_parts.py：自动分割的手臂块旋转 3° 就出现封闭空洞，
把外扩从 10px 加到 16px 也只能勉强到 6°）。连续变形场从构造上就不可能裂。
"""
import math

import numpy as np

RAW_W, RAW_H = 692.0, 720.0
MW, MH = 24, 24          # 网格分辨率，Java 侧必须一致

# 骨表。pivot=支点；amp=幅度上限（度 / 缩放比）；wind=风对角度场的附加增益（度）。
# 权重场参数：win=(中心, 全权重半宽, 归零半宽)；fade=(全权重 y 上限, 过渡宽度)；
#            axis=(x0,y0,x1,y1) 骨段；r=(全权重距, 归零距)；ell=(cx,cy,rx,ry,soft)。
# 幅度表：标定工具（tools/fit_amps.py）只改这一行，骨表与驱动上限自动跟随。
# 幅度标定（tools/calibrate.py，判据 = 单骨「驱动角+风附加角」两向都干净，留 1.35× 余量）：
# 旧值取的是「只测正方向的 0.85× 分析上限」，而发丝/尾鳍的 apex 固定，正反两向的
# 挤压完全不同 —— 实测 hair_l 正向 7.65° 干净、反向 6.2° 就掐到 0.53，于是旧值
# 全部贴在折痕边界上（余量 0.95~1.18×）。下面这组是双向标定后 safe/1.35 的结果。
AMP = dict(head_rot=4.20, hair_l=(3.20, 1.70), hair_r=(2.85, 1.55), hair_f=(2.50, 1.00),
           tail=(7.40, 2.00), arm_l=7.40, arm_r=7.45, torso=0.030, head_dx=6.0, head_dy=6.0,
           blink=0.85, wind=1.00, hand_l=10.5, hand_r=10.5)

# 动画里真实出现过的极端姿态（各自驱动上限的比例）。
# 校验只看这些姿态：动画永远不会比它们更夸张，因此不需要「所有驱动同时拉满」的假想极值。
POSES = {
    "idle":     dict(breath=0.45, head_dy=-0.25, wind=0.30, blink=0.0),
    "blink":    dict(blink=1.00, head_dy=0.20, breath=0.40),
    "turn":     dict(head_rot=1.00, head_dx=0.60, hair_l=0.40, hair_r=0.30, wind=0.50, breath=0.30),
    "nod":      dict(head_dy=1.00, breath=0.60, hair_f=0.30, hair_l=0.25, hair_r=0.25),
    "wave":     dict(arm_r=-0.70, hand_r=1.00, arm_l=-0.30, head_dx=-0.40, hair_r=0.50, wind=0.60,
                     breath=0.40),
    "tail":     dict(tail=1.00, wind=0.70, breath=0.40, head_rot=-0.20),
    "hairflow": dict(hair_l=1.00, hair_r=1.00, hair_f=1.00, wind=1.00, breath=0.30, head_rot=0.30),
    "squash":   dict(breath=1.00, head_dy=0.50, head_rot=-0.50, tail=0.50, wind=0.35),
    "wrist":    dict(hand_r=1.00, hand_l=-0.50, arm_r=0.30, breath=0.30),
}

# 各驱动的「基准幅度」：比例姿态按它缩放。
# 必须严格等于 driver_limit（否则会出现「姿态里 breath=1.0 被当成 0.03、
# 再在 disp 里乘一次 0.03」的二次缩放，实测位移会比预期小 900 倍）。
# 单位：发丝/尾/臂=度，手/头平移=像素，呼吸/眨眼/风=0..1 归一量。

def apply_pose(name):
    """把 POSES 里的比例展开成各驱动的绝对值。"""
    d = blank()
    for k, f in POSES[name].items():
        d[k] = BASE_AMP[k] * f
    return d

BONES = {
    "head":  dict(kind="rot", pivot=(300.0, 442.0), amp=AMP["head_rot"],
                  win=(300.0, 205.0, 335.0), fade=(392.0, 150.0)),
    "hairL": dict(kind="rot", pivot=(168.0, 408.0), amp=AMP["hair_l"][0], wind=AMP["hair_l"][1],
                  k=1.55, ts=0.30, axis=(168.0, 408.0, 88.0, 652.0), r=(34.0, 74.0)),
    "hairR": dict(kind="rot", pivot=(498.0, 415.0), amp=AMP["hair_r"][0], wind=AMP["hair_r"][1],
                  k=1.55, ts=0.30, axis=(498.0, 415.0, 545.0, 660.0), r=(34.0, 74.0)),
    # 软边只留 1.35×r：r1 曾取 34（软边伸到 80px），把 69px 外的右手也带上了 0.14 权重
    # ——甩刘海时手会被拖着动 0.6px。收到 (20,26) 后软边 61px，够覆盖刘海、够不到手。
    "hairF": dict(kind="rot", pivot=(450.0, 330.0), amp=AMP["hair_f"][0], wind=AMP["hair_f"][1],
                  k=1.35, ts=0.35, axis=(450.0, 330.0, 432.0, 452.0), r=(20.0, 26.0)),
    # 支点放在「鳍根」（590,518）而不是身体中心：绕远端支点转时，远离支点的
    # 那圈权重过渡带会产生强切向梯度（实测 -7° 局部压缩到 0.43、面积 0.46）。
    # 绕根部转只用 9° 就能把鳍尖甩出 17px，且过渡带落在鳍身上。
    "tail":  dict(kind="rot", pivot=(588.0, 518.0), amp=AMP["tail"][0], wind=AMP["tail"][1],
                  k=1.60, ts=0.35, axis=(588.0, 518.0, 692.0, 455.0), r=(30.0, 48.0)),
    # 手臂过渡带 k=1.50（带宽 30px）是旧的「折痕边界」值，也是挥手压缩的主犯：
    # 挥手时手臂绕肩转 -5.2°，手臂权重场自己的散度在手掌前缘贡献 -0.17，
    # 与手掌平移场的 -0.26 相乘，把 (433,508) 的主伸缩压到 0.45（判据 0.55）。
    # 带宽改成 2.80×r（56px）后梯度降 46%，该点散度只剩 -0.09；
    # 手臂幅度只有 7.4°、贴图里手臂本身就只有 40px 宽，加宽过渡带不会「带动上半身」。
    "armL":  dict(kind="rot", pivot=(222.0, 440.0), amp=AMP["arm_l"], k=2.80, ts=0.50,
                  axis=(222.0, 440.0, 214.0, 500.0), r=(20.0, 30.0)),
    "armR":  dict(kind="rot", pivot=(372.0, 440.0), amp=AMP["arm_r"], k=2.80, ts=0.50,
                  axis=(372.0, 440.0, 378.0, 500.0), r=(20.0, 30.0)),
    # 手掌平移：挥手如果只靠肩关节旋转，手掌最多走 8px（贴图 692 宽，屏幕上约 4px，看不见）；
    # 旋转角再往上加就会把手臂掐出折痕（实测 14° 时局部伸缩掉到 0.39）。
    # 平移场是「刚体」的：只有权重过渡带里才有形变，所以能给出 20px 以上的可见摆幅。
    # 外径 86.0px 是保护区的上限：再大就会越过下巴去拖脸芯（旧值 r34·k1.55 = 86.7px，
    # 离脸芯最近的点留 5px 余量）。**内核半径与 k 要分开调**：外径 = r·(1+k) 固定时，
    # 过渡带宽度 = r·k = 外径 - r，所以内核越小带宽越大、梯度越小。
    # 旧值 r=34 的内核比整只手掌还大（掌 37×40），白白把带宽压到 52.7px；
    # 收到 r=20（过渡带 66px，梯度降 21%）后，掌上距心 27px 处仍有 0.97 权重，
    # 手感不变，而 (433,508) 的散度从 -0.26 降到 -0.21。
    "handL": dict(kind="trans", c=(214.0, 496.0), amp=AMP["hand_l"], r=20.0, k=3.30,
                  dir=(1.0, -0.35)),
    "handR": dict(kind="trans", c=(378.0, 493.0), amp=AMP["hand_r"], r=20.0, k=3.30,
                  dir=(1.0, -0.35)),
    # fade 上界 440 → 415：440 时「呼吸场」从胸线以下才开始，颈根 (295,430) 与胸口
    # (295,470) 拿到的权重是 0.00 / 0.40，动画里胸口峰值只有 1.75px（判据 1.8）、
    # 脖子整段一动不动 —— 这正是「上半身像纸片」的来源。呼吸本来就该把胸口连同
    # 颈根一起抬起来。改 415 后颈根拿到 0.16、胸口 0.78，峰值 0.9px / 3.6px。
    # 下界 690 与横向 win 不动，腰腹与手臂的既有判据不受影响。
    "torso": dict(kind="scale", pivot=(300.0, 620.0), amp=AMP["torso"], squeeze=0.45,
                  win=(300.0, 80.0, 140.0), fade=(415.0, 690.0, 70.0)),
    "eyeL":  dict(kind="blink", pivot=(227.0, 348.0), amp=AMP["blink"],
                  ell=(227.0, 348.0, 26.0, 32.0, 26.0), close_y=373.0),
    "eyeR":  dict(kind="blink", pivot=(364.0, 338.0), amp=AMP["blink"],
                  ell=(364.0, 338.0, 28.0, 33.0, 26.0), close_y=364.0),
}

# ---- 保护区：脸芯椭圆 ----------------------------------------------------
# 来源：model_fit.py 第 2 步对「皮肤块」的重心与半轴拟合，再收进 shrink 倍。
# 用途：Java 侧与 Python 侧用同一份定义检查「发丝 / 尾巴 / 手 / 手臂都不许拖走脸」，
#       所以它必须是模型自己的量，不能是手工画的方框（旧版方框是手画的，改场之后就失真了）。
FACE = dict(cx=295.5, cy=339.5, rx=138.7, ry=104.7, shrink=0.75)


def face_core(scale=1.0):
    """返回 (cx, cy, rx, ry)：脸芯椭圆。scale=1.0 即定义本身。"""
    return (FACE["cx"], FACE["cy"],
            FACE["rx"] * FACE["shrink"] * scale, FACE["ry"] * FACE["shrink"] * scale)


def in_face(x, y, scale=1.0):
    cx, cy, rx, ry = face_core(scale)
    dx = (x - cx) / rx
    dy = (y - cy) / ry
    return dx * dx + dy * dy <= 1.0


# 驱动量（归一化前后由动画层填）。单位：角度=度，平移=像素，breath/blink/wind=0..1。
# 不挂在骨上的驱动幅度（像素 / 比例 / 0~1 权重）。
# 骨上旋转幅度存在 BONES[*]["amp"]；wind 会额外叠加到发丝与尾鳍上。
DRIVER_AMP = dict(head_dx=AMP["head_dx"], head_dy=AMP["head_dy"], breath=1.0, blink=1.0,
                   wind=AMP["wind"], hand_l=AMP["hand_l"], hand_r=AMP["hand_r"])

DRIVERS = ("head_rot", "head_dx", "head_dy", "hair_l", "hair_r", "hair_f",
           "tail", "arm_l", "arm_r", "hand_l", "hand_r", "breath", "blink", "wind")

# 驱动 → 骨（旋转驱动的幅度写在骨上；像素平移类直接写 DRIVER_AMP）
DRV_BONE = {"head_rot": "head", "hair_l": "hairL", "hair_r": "hairR", "hair_f": "hairF",
            "tail": "tail", "arm_l": "armL", "arm_r": "armR",
            "hand_l": "handL", "hand_r": "handR"}


def driver_limit(drv):
    """驱动**本身**的裁剪上限（不含 wind 附加角）。

    动画层按这个值裁剪，得到的是「驱动角」；发丝/尾鳍真正转过的角度是
    driver + wind×骨风增益，其上限才是 DRIVER_MAX = driver_limit + wind×MAX_WIND。
    """
    if drv in ("breath", "blink", "wind"):
        return 1.0
    bone = DRV_BONE.get(drv)
    if bone and "amp" in BONES[bone]:
        return abs(BONES[bone]["amp"])
    return abs(DRIVER_AMP[drv])


def _driver_max():
    """驱动在「驱动角 + 风附加角」意义下的总上限，供 apply_amps / max_pose 共用。"""
    m = {}
    for drv in DRIVERS:
        bone = DRV_BONE.get(drv)
        w = abs(BONES[bone].get("wind", 0.0)) * abs(DRIVER_AMP["wind"]) if bone else 0.0
        m[drv] = driver_limit(drv) + w
    return m


DRIVER_MAX = _driver_max()

BONE_DRV = {b: d for d, b in DRV_BONE.items()}
BONE_DRV.update({"eyeL": "blink", "eyeR": "blink", "torso": "breath"})


def bone_total(name):
    """骨「拉满」时对应驱动的取值：发丝/尾鳍含风附加角，眼/躯干这类 0..1 驱动就是 1。"""
    drv = BONE_DRV[name]
    return 1.0 if drv in ("breath", "blink", "wind") else DRIVER_MAX[drv]

# 比例姿态的缩放基准 = 各驱动自身上限。**必须由 driver_limit 推导，不要另写一份**：
# 历史上这里写成 AMP["torso"]=0.03，而 disp 里又乘了一次呼吸幅度，
# 结果姿态里的 breath 只有 0.09% 的缩放（「呼吸位移 0.1px」的假通过）。
BASE_AMP = {k: driver_limit(k) for k in DRIVERS}

assert all(abs(BASE_AMP[k] - driver_limit(k)) < 1e-12 for k in DRIVERS), "BASE_AMP 与 driver_limit 脱钩"


# 发丝/尾的角度驱动对应哪根骨
STRAND_DRIVER = {"hairL": "hair_l", "hairR": "hair_r", "hairF": "hair_f",
                 "tail": "tail", "armL": "arm_l", "armR": "arm_r"}


def blank():
    """返回全零驱动，作为动画的起点。"""
    return {k: 0.0 for k in DRIVERS}

def set_driver(d, name, val):
    """按驱动名写入原始值（不做夹取，夹取由动画侧按 DRIVER_AMP 负责）。"""
    d[name] = float(val)
    return d


def apply_amps(d):
    """按各驱动**本身**的上限裁剪，防止动画层手滑写出离谱角度。

    发丝/尾鳍的最终角度还要加上 wind 附加角，两者之和由 DRIVER_MAX 封顶。
    """
    for k in DRIVERS:
        v = driver_limit(k)
        if k in d:
            d[k] = max(-v, min(v, d[k]))
    return d


def clamp01(v):
    return 0.0 if v < 0.0 else (1.0 if v > 1.0 else v)


def smooth(v):
    """smoothstep：两端导数为 0，所以不同骨的场拼接处不会有折线。"""
    v = clamp01(v)
    return v * v * (3.0 - 2.0 * v)


def softstep(d, inner, outer):
    """d ≤ inner → 1；d ≥ outer → 0；中间平滑过渡。inner ≥ outer 时退化为硬阶跃。"""
    if outer <= inner:
        return 1.0 if d <= inner else 0.0
    return smooth((outer - d) / (outer - inner))


def seg_dist(x, y, x0, y0, x1, y1):
    """点到线段的距离，以及在线段上的投影比例 t。"""
    vx, vy = x1 - x0, y1 - y0
    L2 = vx * vx + vy * vy
    t = 0.0 if L2 <= 0.0 else clamp01(((x - x0) * vx + (y - y0) * vy) / L2)
    dx, dy = x - (x0 + vx * t), y - (y0 + vy * t)
    return math.sqrt(dx * dx + dy * dy), t


def rot_xy(x, y, px, py, rad):
    """绕 (px,py) 转 rad 弧度后的位移向量。"""
    rx, ry = x - px, y - py
    ca, sa = math.cos(rad) - 1.0, math.sin(rad)
    return rx * ca - ry * sa, rx * sa + ry * ca


def w_head(x, y, b=None):
    """头部权重：颈线以上为 1，向下 fade[1] 像素内平滑归零；横向出头顶后收窄。"""
    b = b or BONES["head"]
    cx, full, zero = b["win"]
    y_full, y_fade = b["fade"]
    wy = smooth((y_full - y) / y_fade)
    wx = softstep(abs(x - cx), full, zero)
    return wy * wx


def w_strand(x, y, b):
    """发丝/骨段权重：沿轴向向梢部递增，径向过渡带随半径同比放大。

    固定宽度的过渡带在梢部会掐出折痕：那里位移最大而带宽固定 → 局部压缩到 0。
    改成带宽 = k·R 后，|grad w| ≈ 1.5/(k·R) 与 R 成反比，
    而位移正比于到支点的距离 → 两者乘积近似恒定，整根骨上不再出现折痕。

    轴向用 smooth(t / ts) 而不是 smooth(t)：ts < 1 让权重在骨的中段就饱和，
    「整根发丝一起摆」而不是「只有梢尖抖一下」。轴向斜坡越靠近支点越便宜
    （那里力臂短、位移小），所以斜坡该短而靠根。
    """
    x0, y0, x1, y1 = b["axis"]
    d, t = seg_dist(x, y, x0, y0, x1, y1)
    r0, r1 = b["r"]
    R = r0 + (r1 - r0) * t
    return softstep(d, R, R * (1.0 + b["k"])) * smooth(t / b.get("ts", 1.0))


def w_torso(x, y, b=None):
    """躯干权重：只盖住身体（收紧横向窗口，避免把双手拉进呼吸缩放）。"""
    b = b or BONES["torso"]
    cx, full, zero = b["win"]
    y_full, y_end, y_fade = b["fade"]
    wy = smooth((y - y_full) / y_fade) * smooth((y_end - y) / y_fade)
    wx = softstep(abs(x - cx), full, zero)
    return wy * wx


def w_eye(x, y, b):
    """眼睛权重：椭圆内为 1，向外 soft 像素（真实像素距）平滑到 0。"""
    cx, cy, rx, ry, soft = b["ell"]
    dx = max(0.0, abs(x - cx) - rx)
    dy = max(0.0, abs(y - cy) - ry)
    return softstep(math.hypot(dx, dy), 0.0, soft)


def w_hand(x, y, b):
    """手掌权重：以掌心为圆心的径向软罩，半径 r 内满权重，r(1+k) 处归零。"""
    cx, cy = b["c"]
    r = b["r"]
    return softstep(math.hypot(x - cx, y - cy), r, r * (1.0 + b["k"]))


def disp(x, y, d):
    """核心：给定驱动量，返回该点的位移 (dx, dy)。Java 侧是同一套公式。

    结构层按权重归一化混合；细节层（眨眼、呼吸）在归一化之后叠加。
    """
    ax = ay = 0.0
    wsum = 0.0

    # 头：绕颈点旋转 + 摇头/点头平移
    w = w_head(x, y)
    if w > 0.0:
        hx, hy = BONES["head"]["pivot"]
        ux, uy = rot_xy(x, y, hx, hy, math.radians(d["head_rot"]))
        ax += w * (ux + d["head_dx"])
        ay += w * (uy + d["head_dy"])
        wsum += w

    # 长发 / 尾鳍：绕各自发根旋转；wind 作为整体的随风摆，按骨分配增益
    for name in ("hairL", "hairR", "hairF", "tail"):
        b = BONES[name]
        w = w_strand(x, y, b)
        if w > 0.0:
            px, py = b["pivot"]
            ang = math.radians(d[STRAND_DRIVER[name]] + d["wind"] * b["wind"])
            ux, uy = rot_xy(x, y, px, py, ang)
            ax += w * ux
            ay += w * uy
            wsum += w

    # 双臂
    for name in ("armL", "armR"):
        b = BONES[name]
        w = w_strand(x, y, b)
        if w > 0.0:
            px, py = b["pivot"]
            ux, uy = rot_xy(x, y, px, py, math.radians(d[STRAND_DRIVER[name]]))
            ax += w * ux
            ay += w * uy
            wsum += w

    k = wsum if wsum > 1.0 else 1.0
    ax /= k
    ay /= k

    # 细节层 1：呼吸（纵向缩放，横向压 squeeze 倍）
    w = w_torso(x, y)
    if w > 0.0 and d["breath"] != 0.0:
        b = BONES["torso"]
        px, py = b["pivot"]
        sy = 1.0 + d["breath"] * b["amp"]
        sx = 1.0 - d["breath"] * b["amp"] * b["squeeze"]
        ax += w * (x - px) * (sx - 1.0)
        ay += w * (y - py) * (sy - 1.0)

    # 细节层 2：眨眼 —— 整块眼区压到睑线（保证满幅，不被头部权重摊薄）
    if d["blink"] > 0.0:
        for name in ("eyeL", "eyeR"):
            b = BONES[name]
            w = w_eye(x, y, b)
            if w > 0.0:
                ay += w * (b["close_y"] - y) * b["amp"] * d["blink"]

    # 细节层 3：手掌平移（挥手的主体）—— 同样是刚体位移，不参与权重分摊
    for name, drv in (("handL", "hand_l"), ("handR", "hand_r")):
        v = d[drv]
        if v != 0.0:
            w = w_hand(x, y, BONES[name])
            if w > 0.0:
                dx_, dy_ = BONES[name]["dir"]
                ax += w * v * dx_
                ay += w * v * dy_

    return ax, ay


def eye_zone(x, y, pad=18.0):
    """点在眼区（含软边）内则返回该眼名，供验证器豁免「眼睛本来就该被压扁」。"""
    for name in ("eyeL", "eyeR"):
        cx, cy, rx, ry, soft = BONES[name]["ell"]
        dx = max(0.0, abs(x - cx) - rx)
        dy = max(0.0, abs(y - cy) - ry)
        if math.hypot(dx, dy) <= soft + pad:
            return name
    return None


def mesh(d, w=None, h=None):
    """给 Canvas.drawBitmapMesh 用的目标点表（源点均匀铺满整张贴图）。

    返回长度 (MW+1)*(MH+1)*2 的列表：[x0,y0, x1,y1, ...]，行优先。
    """
    w = RAW_W if w is None else w
    h = RAW_H if h is None else h
    out = []
    for j in range(MH + 1):
        sy = h * j / MH
        for i in range(MW + 1):
            sx = w * i / MW
            dx, dy = disp(sx, sy, d)
            out.append(sx + dx)
            out.append(sy + dy)
    return out


def jacobian(x, y, d, h=1.0):
    """数值雅可比：判断局部是否被拉伸/翻转/剪切过头。

    返回 (最大伸缩比, 最小伸缩比, 剪切, 行列式)。行列式 < 0 表示网格翻了 ——
    画面上会看到贴图被掐出尖角，这是最容易一眼看穿的假动作。
    """
    ddx = (disp(x + h, y, d)[0] - disp(x - h, y, d)[0]) / (2 * h)
    ddy = (disp(x, y + h, d)[1] - disp(x, y - h, d)[1]) / (2 * h)
    dxy = (disp(x + h, y, d)[1] - disp(x - h, y, d)[1]) / (2 * h)
    dyx = (disp(x, y + h, d)[0] - disp(x, y - h, d)[0]) / (2 * h)
    a, b, c, e = 1.0 + ddx, dxy, dyx, 1.0 + ddy
    det = a * e - b * c
    t1 = math.hypot(a + e, b - c) / 2.0
    t2 = math.hypot(a - e, b + c) / 2.0
    return t1 + t2, abs(t1 - t2), abs((a - e) / 2.0) + abs((b + c) / 2.0), det


def max_pose():
    """发布动画里可能出现的最大驱动组合。

    每个骨：驱动夹到自身限位，风再额外叠一个附加角 —— 这正是 strand() 的算法，
    所以它就是「单骨可达上界」。**不要把 DRIVER_MAX 直接写进驱动**：
    DRIVER_MAX 已经含风附加角，再配 wind=1.0 会把风算两遍（发丝多摆 2.4°）。
    """
    d = blank()
    for k in DRIVERS:
        d[k] = 1.0 if k == "wind" else driver_limit(k)
    d["hair_r"] = -d["hair_r"]
    d["arm_l"] = -d["arm_l"]
    return apply_amps(d)


# =====================================================================
# 下面是 numpy 向量化实现（只用于分析与验证；发布代码走标量版）。
# 两者必须逐点一致 —— tools/verify_rig.py 第 1 节会抽样比对。
# =====================================================================

def _smooth_np(v):
    v = np.clip(v, 0.0, 1.0)
    return v * v * (3.0 - 2.0 * v)


def _softstep_np(d, inner, outer):
    """内外半径都可以是数组（radius 随 t 变化时 outer 是数组）。"""
    inner = np.asarray(inner, dtype=float)
    outer = np.asarray(outer, dtype=float)
    span = outer - inner
    hard = span <= 1e-9
    span = np.where(hard, 1.0, span)
    soft = _smooth_np((outer - d) / span)
    return np.where(hard, (d <= inner).astype(float), soft)


def _seg_dist_np(X, Y, x0, y0, x1, y1):
    vx, vy = x1 - x0, y1 - y0
    L2 = vx * vx + vy * vy
    if L2 <= 0.0:
        t = np.zeros_like(X)
    else:
        t = np.clip(((X - x0) * vx + (Y - y0) * vy) / L2, 0.0, 1.0)
    return np.hypot(X - (x0 + vx * t), Y - (y0 + vy * t)), t


def w_head_np(X, Y, b=None):
    b = b or BONES["head"]
    cx, full, zero = b["win"]
    y_full, y_fade = b["fade"]
    return _smooth_np((y_full - Y) / y_fade) * _softstep_np(np.abs(X - cx), full, zero)


def w_strand_np(X, Y, b):
    x0, y0, x1, y1 = b["axis"]
    d, t = _seg_dist_np(X, Y, x0, y0, x1, y1)
    r0, r1 = b["r"]
    R = r0 + (r1 - r0) * t
    return _softstep_np(d, R, R * (1.0 + b["k"])) * _smooth_np(t / b.get("ts", 1.0))


def w_torso_np(X, Y, b=None):
    b = b or BONES["torso"]
    cx, full, zero = b["win"]
    y_full, y_end, y_fade = b["fade"]
    return (_smooth_np((Y - y_full) / y_fade) * _smooth_np((y_end - Y) / y_fade)
            * _softstep_np(np.abs(X - cx), full, zero))


def w_eye_np(X, Y, b):
    cx, cy, rx, ry, soft = b["ell"]
    dx = np.maximum(0.0, np.abs(X - cx) - rx)
    dy = np.maximum(0.0, np.abs(Y - cy) - ry)
    return _softstep_np(np.hypot(dx, dy), 0.0, soft)


def w_hand_np(X, Y, b):
    cx, cy = b["c"]
    r = b["r"]
    return _softstep_np(np.hypot(X - cx, Y - cy), r, r * (1.0 + b["k"]))


def _rot_np(X, Y, px, py, rad):
    rx, ry = X - px, Y - py
    ca, sa = np.cos(rad) - 1.0, np.sin(rad)
    return rx * ca - ry * sa, rx * sa + ry * ca


def disp_grid(d, xs, ys):
    """网格化位移。xs/ys 为 1 维坐标数组，返回 (X, Y, DX, DY)，形状 (len(ys), len(xs))。"""
    X, Y = np.meshgrid(np.asarray(xs, float), np.asarray(ys, float))
    AX = np.zeros_like(X)
    AY = np.zeros_like(X)
    WS = np.zeros_like(X)

    w = w_head_np(X, Y)
    hx, hy = BONES["head"]["pivot"]
    ux, uy = _rot_np(X, Y, hx, hy, math.radians(d["head_rot"]))
    AX += w * (ux + d["head_dx"])
    AY += w * (uy + d["head_dy"])
    WS += w

    for name in ("hairL", "hairR", "hairF", "tail", "armL", "armR"):
        b = BONES[name]
        w = w_strand_np(X, Y, b)
        px, py = b["pivot"]
        if name == "armL" or name == "armR":
            ang = d[STRAND_DRIVER[name]]
        else:
            ang = d[STRAND_DRIVER[name]] + d["wind"] * b["wind"]
        ux, uy = _rot_np(X, Y, px, py, math.radians(ang))
        AX += w * ux
        AY += w * uy
        WS += w

    k = np.maximum(1.0, WS)
    AX = AX / k
    AY = AY / k

    b = BONES["torso"]
    w = w_torso_np(X, Y)
    px, py = b["pivot"]
    sy = 1.0 + d["breath"] * b["amp"]
    sx = 1.0 - d["breath"] * b["amp"] * b["squeeze"]
    AX += w * (X - px) * (sx - 1.0)
    AY += w * (Y - py) * (sy - 1.0)

    if d["blink"] > 0.0:
        for name in ("eyeL", "eyeR"):
            b = BONES[name]
            w = w_eye_np(X, Y, b)
            AY += w * (b["close_y"] - Y) * b["amp"] * d["blink"]

    for name, drv in (("handL", "hand_l"), ("handR", "hand_r")):
        v = d[drv]
        if v != 0.0:
            b = BONES[name]
            w = w_hand_np(X, Y, b)
            dx_, dy_ = b["dir"]
            AX += w * v * dx_
            AY += w * v * dy_
    return X, Y, AX, AY


def eye_zone_mask(X, Y, pad=18.0):
    """眼区掩码（含软边）：这些区域的形变有意很剧烈（眨眼就是要把眼睛压扁）。"""
    m = np.zeros(X.shape, bool)
    for name in ("eyeL", "eyeR"):
        cx, cy, rx, ry, soft = BONES[name]["ell"]
        dx = np.maximum(0.0, np.abs(X - cx) - rx)
        dy = np.maximum(0.0, np.abs(Y - cy) - ry)
        m |= np.hypot(dx, dy) <= soft + pad
    return m


def deform_stats(d, mask, step=2, pad=18.0):
    """在角色轮廓内扫描一次形变，返回各项极值（供验收判据）。

    mask 为 (H,W) 布尔的角色实心区。返回 dict：
      area 网格单元面积比最小值 / slo,shi 主伸缩极值 / shear 剪切最大值
      disp 最大位移 / flip 翻面采样点数 / at 最差点的坐标
    """
    ys = np.arange(0.0, RAW_H, float(step))
    xs = np.arange(0.0, RAW_W, float(step))
    X, Y, DX, DY = disp_grid(d, xs, ys)
    sz = mask[::step, ::step]
    ey = eye_zone_mask(X, Y, pad)
    sel = sz & (~ey)

    gx = float(step)
    ax = np.gradient(DX, axis=1) / gx
    bx = np.gradient(DX, axis=0) / gx
    cx = np.gradient(DY, axis=1) / gx
    ex = np.gradient(DY, axis=0) / gx
    a = 1.0 + ax
    b = cx          # ∂dx/∂y
    c = bx          # ∂dy/∂x
    e = 1.0 + ex
    det = a * e - b * c
    t1 = np.hypot(a + e, b - c) / 2.0
    t2 = np.hypot(a - e, b + c) / 2.0
    s_max = t1 + t2
    s_min = np.abs(t1 - t2)
    shear = np.abs((a - e) / 2.0) + np.abs((b + c) / 2.0)
    disp = np.hypot(DX, DY)

    def worst(arr):
        v = np.where(sel, arr, -np.inf)
        idx = np.unravel_index(np.argmax(v), v.shape)
        return float(v[idx]), (float(xs[idx[1]]), float(ys[idx[0]]))

    def worst_min(arr):
        v = np.where(sel, arr, np.inf)
        idx = np.unravel_index(np.argmin(v), v.shape)
        return float(v[idx]), (float(xs[idx[1]]), float(ys[idx[0]]))

    shi, at = worst(s_max)
    slo, _ = worst_min(s_min)
    shr, _ = worst(shear)
    dsp, _ = worst(disp)
    flip = int(np.count_nonzero(sel & (det <= 0.0)))
    area = cell_area_ratio(d, step, keep=~ey)

    # 网格单元面积（用同一网格，单元可能被眼区覆盖，这里只看整体最小值）
    return dict(area=area, slo=slo, shi=shi, shear=shr, disp=dsp, flip=flip, at=at)


def cell_area_ratio(d, step=2, keep=None):
    """用 step 网格的四角点算单元面积比的最小值（<1 表示被压缩，≤0 表示折叠）。

    keep 可选，给出「计入统计」的布尔掩码（同分辨率的采样点），
    用来把眼芯这种「本该被压扁」的区域排除在外。
    """
    gx = float(step)
    ys = np.arange(0.0, RAW_H, gx)
    xs = np.arange(0.0, RAW_W, gx)
    X, Y, DX, DY = disp_grid(d, xs, ys)
    PX = X + DX
    PY = Y + DY
    p00 = (PX[:-1, :-1], PY[:-1, :-1])
    p10 = (PX[:-1, 1:], PY[:-1, 1:])
    p01 = (PX[1:, :-1], PY[1:, :-1])
    p11 = (PX[1:, 1:], PY[1:, 1:])

    def tri(p, q, r):
        return np.abs((q[0] - p[0]) * (r[1] - p[1]) - (q[1] - p[1]) * (r[0] - p[0])) / 2.0

    rat = (tri(p00, p10, p11) + tri(p00, p11, p01)) / (gx * gx)
    if keep is not None:
        k = keep[:-1, :-1] & keep[:-1, 1:] & keep[1:, :-1] & keep[1:, 1:]
        if k.any():
            rat = np.where(k, rat, np.inf)
    return float(np.min(rat))


def field_grad_max(fn, step=2.0):
    """某个权重场的 |∇w| 最大值与位置（用来抓「硬边」造成的折线）。"""
    xs = np.arange(0.0, RAW_W, step)
    ys = np.arange(0.0, RAW_H, step)
    X, Y = np.meshgrid(xs, ys)
    F = fn(X, Y)
    gx = np.gradient(F, axis=1) / step
    gy = np.gradient(F, axis=0) / step
    g = np.hypot(gx, gy)
    idx = np.unravel_index(np.argmax(g), g.shape)
    return float(g[idx]), (float(xs[idx[1]]), float(ys[idx[0]]))


def mesh_stats(d, mask=None):
    """按 Java 渲染网格（MW×MH 单元，顶点 (MW+1)×(MH+1)）算单元面积比与翻面数。

    这是「真正被画出来」的那层网格，判据以此为准：
      单元格由两个三角形拼成，任一三角形有向面积为负 = 该格翻面。
    """
    xs = np.linspace(0.0, RAW_W, MW + 1)
    ys = np.linspace(0.0, RAW_H, MH + 1)
    X, Y, DX, DY = disp_grid(d, xs, ys)
    PX = X + DX
    PY = Y + DY
    cw = RAW_W / MW
    ch = RAW_H / MH

    def sarea(p, q, r):
        return ((q[0] - p[0]) * (r[1] - p[1]) - (q[1] - p[1]) * (r[0] - p[0])) / 2.0

    p00 = (PX[:-1, :-1], PY[:-1, :-1])
    p10 = (PX[:-1, 1:], PY[:-1, 1:])
    p01 = (PX[1:, :-1], PY[1:, :-1])
    p11 = (PX[1:, 1:], PY[1:, 1:])
    a1 = sarea(p00, p10, p11)
    a2 = sarea(p00, p11, p01)
    cells = np.abs(a1) + np.abs(a2)
    ratio = cells / (cw * ch)
    flip = (a1 <= 0) | (a2 <= 0)
    # 眼芯允许被「眨」压扁（那正是闭眼的形变），只对折叠与眼外区域设限
    _gx, _gy = np.meshgrid(np.arange(MW) * cw + cw / 2, np.arange(MH) * ch + ch / 2)
    core = eye_zone_mask(_gx, _gy, 0.0)
    # 只统计「碰到角色实心像素」的单元：透明区折叠看不见
    if mask is None:
        sel = np.ones_like(ratio, dtype=bool)
    else:
        m = np.asarray(mask, dtype=bool)
        sel = np.zeros_like(ratio, dtype=bool)
        for i in range(MH):
            y0, y1 = int(i * ch), int((i + 1) * ch)
            for j in range(MW):
                if m[y0:y1, int(j * cw):int((j + 1) * cw)].any():
                    sel[i, j] = True
    return dict(min_ratio=float(ratio[sel & ~core].min()) if (sel & ~core).any() else 1.0,
                flips=int(np.count_nonzero(flip & sel)),
                cells=int(sel.sum()))

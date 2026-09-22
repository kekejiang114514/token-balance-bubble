#!/usr/bin/env python3
"""静态审计：控件工厂里「造了视图却没挂进视图树」。

背景：Ui.sliderRow() 一度写成 `s.bar = new SeekBar(ctx)`，配了监听、设了范围，
唯独忘了 `box.addView(s.bar)`。于是整行只剩标题和数值标签，滑杆根本不在视图树里 ——
界面上看不见、也拖不动，所有走滑杆的参数（宽度/字号/圆角/不透明度/角色大小…）
就永远调不了。这种漏挂在编译期和单测里都看不出来，只能盯源码，所以做成脚本。

判据（逐个方法体）：
  1. 找出 `x = new <View 类型>(...)`（含 `s.bar = ...` 这种字段赋值，变量名取末段）；
  2. 该变量必须有归宿，满足以下任一：
     a. 出现在某个 `addView(...)` 的参数里；
     b. 出现在 `setContentView(...)` 的参数里；
     c. 被 `return` 出去；
     d. 被赋给别的对象的字段（`holder.field = v;`，等于交给调用方挂载）；
  3. 一条都不满足 → 报错。

有意为之的例外写在 IGNORE 里，必须注明原因，不许空口放行。
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FILES = [
    ROOT / "app/src/com/coco/balancebubble/Ui.java",
    ROOT / "app/src/com/coco/balancebubble/MainActivity.java",
]

VIEW_TYPES = (
    "LinearLayout", "FrameLayout", "RelativeLayout", "ScrollView", "HorizontalScrollView",
    "TextView", "EditText", "Button", "ImageButton", "ImageView", "Switch", "SeekBar",
    "Spinner", "CheckBox", "RadioButton", "ProgressBar", "View", "ViewGroup", "Space",
)
NEW_RE = re.compile(r"(?:^|[;{}\s])([A-Za-z_][\w.]*)\s*=\s*new\s+("
                    + "|".join(VIEW_TYPES) + r")\s*\(")

# (文件名, 变量名) → 原因
IGNORE = {}


def strip_comments(text):
    """去掉注释再分析：注释里出现的 addView 不算数。"""
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    return re.sub(r"//[^\n]*", " ", text)


def calls_with_args(body, fname):
    """取出 body 里所有 fname(...) 调用的实参文本（括号配平，能跨行）。"""
    out = []
    for m in re.finditer(r"\b" + fname + r"\s*\(", body):
        i = body.index("(", m.start())
        depth, j = 0, i
        while j < len(body):
            if body[j] == "(":
                depth += 1
            elif body[j] == ")":
                depth -= 1
                if depth == 0:
                    break
            j += 1
        out.append(body[i:j + 1])
    return out


def method_bodies(text):
    """粗切方法体：按大括号配平，切出 (方法名, 起始偏移, 方法体文本)。"""
    out = []
    for m in re.finditer(r"\n    (?:public |private |static |final |abstract )*"
                         r"[\w<>\[\],\s.]+?(\w+)\s*\([^;{]*\)\s*\{", text):
        i = text.index("{", m.start())
        depth, j = 0, i
        while j < len(text):
            if text[j] == "{":
                depth += 1
            elif text[j] == "}":
                depth -= 1
                if depth == 0:
                    break
            j += 1
        out.append((m.group(1), i, text[i:j + 1]))
    return out


def audit(path):
    text = strip_comments(path.read_text(encoding="utf-8"))
    problems = []
    for name, start, body in method_bodies(text):
        attached_args = (calls_with_args(body, "addView")
                         + calls_with_args(body, "setContentView")
                         + calls_with_args(body, "addViewLayout"))
        for m in NEW_RE.finditer(body):
            full, typ = m.group(1), m.group(2)
            var = full.split(".")[-1]
            if (path.name, var) in IGNORE:
                continue
            word = r"\b" + re.escape(var) + r"\b"
            if any(re.search(word, a) for a in attached_args):
                continue
            if re.search(r"return\b[^;]*" + word, body):
                continue
            # 交给别的对象的字段（holder.field = v;）—— 调用方负责挂载
            if re.search(r"[A-Za-z_][\w.]*\s*=\s*" + word + r"\s*;", body):
                continue
            line = text[:start + m.start()].count("\n") + 1
            problems.append(
                f"{path.name}:{line} {name}() 造了 {typ} 但没有任何归宿"
                f"（变量 {full}）—— 挂到父容器、setContentView、return 或交给调用方都行")
    return problems


def main():
    bad, scanned = [], 0
    for f in FILES:
        if not f.exists():
            print(f"跳过（不存在）：{f}")
            continue
        scanned += 1
        bad += audit(f)
    if bad:
        print("控件挂载审计未通过：")
        for b in bad:
            print("  ✗", b)
        return 1
    print(f"控件挂载审计通过（扫描 {scanned} 个文件，未发现创建后无处安放的视图）")
    return 0


if __name__ == "__main__":
    sys.exit(main())

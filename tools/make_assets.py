# -*- coding: utf-8 -*-
"""从原始美术素材生成应用内的图片资源。

输入：一张白底的角色原图。
输出：app/assets/char.png（透明底角色）、app/res/mipmap/ic_launcher.png（桌面图标）。

用法：
    python3 tools/make_assets.py 原图.png

注意：这个脚本只在需要替换角色素材时才用得上，正常构建不需要执行。
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_CHAR = os.path.join(ROOT, 'app/assets/char.png')
OUT_ICON = os.path.join(ROOT, 'app/res/mipmap/ic_launcher.png')
PREVIEW = os.path.join(ROOT, 'out/assets-preview.png')

THRESH = 45
ERODE = 1
BLUR = 1.1
ICON_SIZE = 432


def cutout(src, thresh=THRESH, erode=ERODE, blur=BLUR):
    """从白底图中抠出角色，返回 RGBA 图。

    思路：从四边向内做 flood fill 找出背景，得到 alpha 掩膜；
    再向内收缩一圈去掉白边残留；最后对边缘做一次高斯模糊得到抗锯齿。
    """
    im = Image.open(src).convert('RGB')
    w, h = im.size
    px = im.load()

    # 1. 白底掩膜：flood fill（迭代式，避免递归深度问题）
    white = [[False] * w for _ in range(h)]
    stack = []
    for x in range(w):
        stack.append((x, 0))
        stack.append((x, h - 1))
    for y in range(h):
        stack.append((0, y))
        stack.append((w - 1, y))

    def is_bg(x, y):
        r, g, b = px[x, y]
        return r > 255 - thresh and g > 255 - thresh and b > 255 - thresh

    while stack:
        x, y = stack.pop()
        if x < 0 or y < 0 or x >= w or y >= h:
            continue
        if white[y][x]:
            continue
        if not is_bg(x, y):
            continue
        white[y][x] = True
        stack.append((x + 1, y))
        stack.append((x - 1, y))
        stack.append((x, y + 1))
        stack.append((x, y - 1))

    # 2. 掩膜转图像
    mask = Image.new('L', (w, h), 255)
    mp = mask.load()
    for y in range(h):
        for x in range(w):
            if white[y][x]:
                mp[x, y] = 0

    # 3. 向内收缩，去掉白边
    for _ in range(erode):
        mask = mask.filter(ImageFilter.MinFilter(3))

    # 4. 边缘柔化
    if blur > 0:
        mask = mask.filter(ImageFilter.GaussianBlur(blur))

    out = im.convert('RGBA')
    out.putalpha(mask)
    return out


def make_icon(char, size=ICON_SIZE):
    """生成圆角方形底 + 角色的桌面图标。"""
    icon = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(icon)

    # 纵向渐变底
    for y in range(size):
        t = y / float(size - 1)
        r = int(37 + (17 - 37) * t)
        g = int(99 + (58 - 99) * t)
        b = int(235 + (160 - 235) * t)
        d.line([(0, y), (size, y)], fill=(r, g, b, 255))

    # 圆角遮罩
    round_mask = Image.new('L', (size, size), 0)
    ImageDraw.Draw(round_mask).rounded_rectangle(
        [0, 0, size - 1, size - 1], radius=int(size * 0.22), fill=255)
    icon.putalpha(round_mask)

    # 角色居中
    c = char.copy()
    c.thumbnail((int(size * 0.74), int(size * 0.74)), Image.LANCZOS)
    icon.alpha_composite(c, ((size - c.width) // 2, (size - c.height) // 2 + int(size * 0.04)))
    return icon


def main():
    if len(sys.argv) < 2:
        sys.stderr.write('用法: python3 tools/make_assets.py 原图.png\n')
        return 2

    char = cutout(sys.argv[1])
    os.makedirs(os.path.dirname(OUT_CHAR), exist_ok=True)
    os.makedirs(os.path.dirname(OUT_ICON), exist_ok=True)
    os.makedirs(os.path.dirname(PREVIEW), exist_ok=True)
    char.save(OUT_CHAR)

    icon = make_icon(char)
    icon.save(OUT_ICON)

    # 拼一张对比图方便肉眼检查抠图效果
    src_img = Image.open(sys.argv[1]).convert('RGB')
    src_img.thumbnail((char.width, char.height))
    pv = Image.new('RGB', (src_img.width + char.width + 12, max(src_img.height, char.height)),
                   (40, 44, 52))
    pv.paste(src_img, (0, 0))
    pv.paste(char, (src_img.width + 12, 0), char)
    pv.save(PREVIEW)

    sys.stderr.write('char  -> %s (%dx%d)\n' % (OUT_CHAR, char.width, char.height))
    sys.stderr.write('icon  -> %s (%dx%d)\n' % (OUT_ICON, icon.width, icon.height))
    sys.stderr.write('预览  -> %s\n' % PREVIEW)
    return 0


if __name__ == '__main__':
    sys.exit(main())

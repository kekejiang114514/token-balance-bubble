package com.coco.balancebubble;

import android.content.Context;

/**
 * 气泡的最终外观：把 {@link Prefs} 里的用户设置换算成像素后的一组定值，
 * 交给 {@link BubbleView} 和 {@link BubbleLayout} 使用。
 *
 * <p>好处是「设置 → 渲染」只有这一条通道：设置页改完直接重建 Style，
 * 不需要在绘图层里到处回读 SharedPreferences。
 */
public class BubbleStyle {

    /** 文字最大宽度（px）。 */
    public float maxWidth;
    /** 最短宽度，防止「¥1」这种短文本的气泡小得像图钉。 */
    public float minWidth;
    public float padH;
    public float padT;
    public float padB;
    public float gap;
    public float tailH;
    public float tailW;
    public float radius;
    public float baseSize;
    public float minSize;
    public float labelSize;
    public int maxLines;
    public int hardMaxLines;
    /** 0~255。 */
    public int alpha = 255;
    public int bg;
    public int ink;
    public int subInk;
    public int errInk;
    public boolean shadow;
    /** true 表示尖角朝上（气泡挂在角色下方）。 */
    public boolean tailUp;
    /** 尺寸变化的补间时长，0 表示不做动画。 */
    public long animMs;
    /** 深色气泡时不画投影，避免白边。 */
    public boolean border;

    public static BubbleStyle from(Context c) {
        float d = c.getResources().getDisplayMetrics().density;
        return build(Prefs.load(c), d);
    }

    /** 从设置构造，d 是 density（dp → px）。 */
    public static BubbleStyle build(Prefs.Draft p, float d) {
        BubbleStyle s = new BubbleStyle();
        Theme th = new Theme(p.theme, p.darkNow());
        s.maxWidth = p.bubbleMaxW * d;
        s.minWidth = Math.min(p.bubbleMinW, p.bubbleMaxW) * d;
        s.padH = p.bubblePadH * d;
        s.padT = p.bubblePadT * d;
        s.padB = p.bubblePadB * d;
        s.gap = Math.max(1f, 2f * d);
        s.tailH = p.bubbleTail * d;
        s.tailW = Math.max(10f * d, p.bubbleTail * 1.9f * d);
        s.radius = p.bubbleRadius * d;
        s.baseSize = p.bubbleFont * d;
        s.minSize = Math.min(p.bubbleMinFont, p.bubbleFont) * d;
        s.labelSize = Math.max(9f, p.bubbleFont * 0.52f) * d;
        s.maxLines = p.bubbleLines;
        s.hardMaxLines = Math.max(p.bubbleLines, p.bubbleHardLines);
        s.alpha = Math.round(255 * p.bubbleAlpha);
        s.bg = th.bubbleBg(p.bubbleBg);
        s.ink = Theme.inkOn(s.bg);
        s.subInk = Theme.subInkOn(s.bg);
        s.errInk = Theme.errInkOn(s.bg);
        s.shadow = p.bubbleShadow;
        s.tailUp = p.bubbleTailUp;
        s.animMs = p.bubbleAnim ? Math.max(0, p.bubbleAnimMs) : 0L;
        s.border = Theme.luminance(s.bg) > 0.55f;
        return s;
    }

    /** 转成排版引擎的约束。 */
    public BubbleLayout.Spec spec() {
        BubbleLayout.Spec sp = new BubbleLayout.Spec();
        sp.maxWidth = maxWidth;
        sp.minWidth = minWidth;
        sp.padH = padH;
        sp.padT = padT;
        sp.padB = padB;
        sp.gap = gap;
        sp.tailH = tailH;
        sp.baseSize = baseSize;
        sp.minSize = minSize;
        sp.maxLines = maxLines;
        sp.hardMaxLines = hardMaxLines;
        sp.labelSize = labelSize;
        return sp;
    }
}

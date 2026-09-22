package com.coco.balancebubble;

import java.util.ArrayList;
import java.util.List;

/**
 * 气泡的自动排版：给定一段文字和一组尺寸约束，算出「用多大字号、折成几行、气泡多宽多高」。
 *
 * <p>策略（按优先级）：
 * <ol>
 *   <li>先用基准字号折行，行数够就用它 —— 字号最大、最好看；</li>
 *   <li>折不下就按固定比例逐级缩小字号，直到最小字号为止；</li>
 *   <li>缩到最小还是折不下，就放宽行数上限（气泡长高），而不是把文字截掉；</li>
 *   <li>放宽到硬上限还放不下才截断，并标记 {@code truncated}（调用方可以据此折叠成「详情」）。</li>
 * </ol>
 *
 * <p>宽度策略：先按最长一行算内容宽度，再加左右内边距；短文本不会撑成一个大方框
 * （有 minWidth 兜底，避免一个「¥1」气泡小得像图钉），长文本不会超过 maxWidth
 * （超宽会被窗口裁掉，表现就是「文字被吞了」）。
 *
 * <p>纯逻辑：文字宽度与行高都由 {@link Metrics} 提供，JVM 上可以用假实现跑单测。
 */
public final class BubbleLayout {

    /** 排版用的量字接口。调用方负责把 {@code size} 应用到真实画笔上。 */
    public interface Metrics {
        /** 设置当前字号（px），后续 width/lineHeight 都按这个字号算。 */
        void setSize(float px);

        /** 量 [from, to) 这段文字的宽度（px）。 */
        float width(String text, int from, int to);

        /** 当前字号下的一行行高（px），一般取 descent - ascent。 */
        float lineHeight();
    }

    /** 尺寸约束。单位统一用 px，dp 换算由调用方做。 */
    public static class Spec {
        public float maxWidth = 300f;
        public float minWidth = 74f;
        public float padH = 15f;
        public float padT = 9f;
        public float padB = 10f;
        public float gap = 2f;
        public float tailH = 9f;
        /** 第一行文字用的基准字号。 */
        public float baseSize = 22f;
        /** 允许缩到的最小字号；再小就看不清了。 */
        public float minSize = 14f;
        /** 每次缩小的比例。0.9 表示缩 10%。 */
        public float shrink = 0.9f;
        /** 正常情况下的最大行数，超过就缩字号。 */
        public int maxLines = 3;
        /** 缩到最小字号后允许放宽到的行数上限。 */
        public int hardMaxLines = 8;
        /** 标题（第一行小字）的字号。 */
        public float labelSize = 11.5f;

        public Spec copy() {
            Spec s = new Spec();
            s.maxWidth = maxWidth;
            s.minWidth = minWidth;
            s.padH = padH;
            s.padT = padT;
            s.padB = padB;
            s.gap = gap;
            s.tailH = tailH;
            s.baseSize = baseSize;
            s.minSize = minSize;
            s.shrink = shrink;
            s.maxLines = maxLines;
            s.hardMaxLines = hardMaxLines;
            s.labelSize = labelSize;
            return s;
        }
    }

    /** 排版结果。 */
    public static class Result {
        public final List<String> lines = new ArrayList<String>();
        /** 折好的标题（可能被省略号截短）；标题为空表示不显示标题行。 */
        public String label = "";
        /** 实际用到的字号。 */
        public float size;
        /** 标题行高（不含 gap）。 */
        public float labelLineH;
        /** 单行行高。 */
        public float lineH;
        /** 气泡总宽（含内边距）。 */
        public float width;
        /** 气泡总高（含内边距与尖角）。 */
        public float height;
        /** 最长一行文字的宽度（含内边距后的净内容宽）。 */
        public float contentWidth;
        /** 是否因超过硬行数上限而被省略号截断。 */
        public boolean truncated;
        /** 折行次数，用于自检「有没有缩过字号」。 */
        public int shrinkSteps;

        public int lineCount() {
            return lines.size();
        }
    }

    private BubbleLayout() {
    }

    public static Result fit(String label, String amount, Spec s, Metrics m) {
        Result r = new Result();
        String text = (amount == null || amount.length() == 0) ? "--" : amount;
        float innerMax = Math.max(1f, s.maxWidth - s.padH * 2f);
        final TextWrap.Width w = new TextWrap.Width() {
            @Override
            public float of(String t, int from, int to) {
                return m.width(t, from, to);
            }
        };

        float minSize = Math.min(s.minSize, s.baseSize);
        float size = Math.max(s.baseSize, 1f);
        int maxLines = Math.max(1, s.maxLines);
        int hardLines = Math.max(maxLines, s.hardMaxLines);
        List<String> best = null;
        boolean truncated = false;
        int steps = 0;
        while (true) {
            m.setSize(size);
            boolean[] t = new boolean[1];
            best = TextWrap.wrap(text, innerMax, maxLines, w, t);
            if (!t[0]) break;                       // 折得下，收工
            if (size <= minSize + 0.01f) {
                // 已经是最小字号还折不下：放宽行数让气泡长高，而不是把文字截掉。
                best = TextWrap.wrap(text, innerMax, hardLines, w, t);
                truncated = t[0];
                break;
            }
            if (steps++ >= 24) {                    // 防御：shrink 配得不合理时不至于死循环
                truncated = t[0];
                break;
            }
            size = Math.max(minSize, size * s.shrink);
        }
        r.shrinkSteps = steps;
        r.truncated = truncated;
        r.size = size;
        r.lines.clear();
        r.lines.addAll(best);

        // 标题：超宽就省略号截短（标题不参与缩字号，它是次要信息）
        String lab = label == null ? "" : label;
        if (lab.length() > 0) {
            m.setSize(s.labelSize);
            r.labelLineH = m.lineHeight();
            r.label = ellipsize(lab, innerMax, m);
        } else {
            r.labelLineH = 0f;
        }

        // 尺寸：宽度跟最长一行走，高度按行数累计
        m.setSize(size);
        r.lineH = m.lineHeight();
        float lw = 0f;
        for (String l : r.lines) lw = Math.max(lw, m.width(l, 0, l.length()));
        float contentW = lw;
        if (r.label.length() > 0) {
            m.setSize(s.labelSize);
            contentW = Math.max(contentW, m.width(r.label, 0, r.label.length()));
        }
        r.contentWidth = contentW;
        float width = contentW + s.padH * 2f;
        if (width < s.minWidth) width = s.minWidth;
        if (width > s.maxWidth) width = s.maxWidth;
        r.width = width;
        r.height = s.padT + (r.label.length() > 0 ? r.labelLineH + s.gap : 0f)
                + Math.max(1, r.lines.size()) * r.lineH + s.padB + s.tailH;
        return r;
    }

    /** 超宽就按字符截短并补省略号；宽度预算保证不超。 */
    static String ellipsize(String text, float maxWidth, Metrics m) {
        if (m.width(text, 0, text.length()) <= maxWidth) return text;
        int n = text.length();
        int lo = 1, hi = n;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (m.width(text, 0, mid) + m.width("" + TextWrap.ELLIPSIS, 0, 1) <= maxWidth) lo = mid;
            else hi = mid - 1;
        }
        return text.substring(0, Math.max(1, lo)) + TextWrap.ELLIPSIS;
    }
}

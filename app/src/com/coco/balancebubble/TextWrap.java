package com.coco.balancebubble;

import java.util.ArrayList;
import java.util.List;

/**
 * 中英混排断行：中日韩字符逐字断，拉丁字母数字按词断，标点不落在行首。
 *
 * <p>写成不依赖 Android 的纯逻辑类，是为了能在 JVM 上直接跑单测
 * （沙箱里没有 Android 运行时，{@code StaticLayout} 跑不起来）。
 *
 * <p><b>不变量：返回的每一行宽度都不超过 maxWidth。</b>
 * 排版方按这个预算决定气泡宽度，一旦有行超出预算，气泡就会比屏幕还宽、
 * 文字被窗口裁掉（表现为「文字显示不全」）。
 */
public class TextWrap {

    /** 测量 [from, to) 这段文字的宽度，单位与 maxWidth 一致。 */
    public interface Width {
        float of(String text, int from, int to);
    }

    /** 不允许出现在行首的标点（中文断行的基本禁则）。 */
    private static final String NO_START = "，。、；：？！）】》」』〉…—～·%,.;:?!)]}";

    /** 省略号，被截断的末行会用它收尾。 */
    public static final char ELLIPSIS = '…';

    /**
     * 把 text 按 maxWidth 折成若干行。
     *
     * @param maxLines 最多几行；超出部分丢弃，并在末行加省略号。传 0 表示不限行数。
     * @return 每行的文字，行尾空白已去掉；空文本返回空列表。
     */
    public static List<String> wrap(String text, float maxWidth, int maxLines, Width w) {
        return wrap(text, maxWidth, maxLines, w, null);
    }

    /**
     * 同 {@link #wrap(String, float, int, Width)}，但额外报告末行是否被省略号截断。
     *
     * <p>调用方不应靠「末行是不是以 … 结尾」来判断截断——正文本身就可能以 … 结尾
     * （例如「正在刷新中…」），那样会误判。
     *
     * @param truncatedOut 长度为 1 的数组，用于回传是否真的发生了截断；可为 null。
     */
    public static List<String> wrap(String text, float maxWidth, int maxLines, Width w,
                                    boolean[] truncatedOut) {
        List<String> out = new ArrayList<String>();
        if (truncatedOut != null) truncatedOut[0] = false;
        if (text == null || text.length() == 0) return out;
        if (maxWidth <= 0) {
            out.add(text);
            return out;
        }
        int n = text.length();
        int i = 0;
        boolean truncated = false;
        while (i < n) {
            if (maxLines > 0 && out.size() == maxLines) {
                truncated = true;
                break;
            }
            int end = lineEnd(text, i, maxWidth, w);
            if (end <= i) end = i + 1;          // 单字比整行还宽时也要前进
            String line = ltrim(rtrim(text.substring(i, end)));
            if (line.length() == 0) {           // 整行都是空白：丢掉，接着往后走
                i = end;
                continue;
            }
            out.add(line);
            i = end;
            while (i < n && text.charAt(i) == ' ') i++;   // 行首不留空格
        }
        if (truncated) {
            String last = out.get(out.size() - 1);
            last = last + ELLIPSIS;
            while (last.length() > 1 && w.of(last, 0, last.length()) > maxWidth) {
                last = last.substring(0, last.length() - 2) + ELLIPSIS;
            }
            out.set(out.size() - 1, last);
        }
        if (truncatedOut != null) truncatedOut[0] = truncated;
        return out;
    }

    /** 从 from 开始，本行最多能放到哪个下标（不含）。返回的行宽保证不超过 maxWidth。 */
    private static int lineEnd(String text, int from, float maxWidth, Width w) {
        int n = text.length();
        int k = from;
        float used = 0;
        int lastBreak = -1;
        while (k < n) {
            int tokEnd = nextTokenEnd(text, k);
            if (tokEnd <= k) tokEnd = k + 1;        // 空 token 也要保证前进
            float tw = w.of(text, k, tokEnd);
            if (k > from && used + tw > maxWidth) break;
            used += tw;
            k = tokEnd;
            if (breakableAfter(text, k)) lastBreak = k;
        }
        if (k == from) {
            // 第一个 token 本身就比整行还宽（很长的英文标识符、字段路径、URL 等）：
            // 按字符硬切。否则这一行会撑破气泡宽度，被屏幕裁掉。
            return fitEnd(text, from, maxWidth, w);
        }
        if (k < n && lastBreak > from) k = lastBreak;
        // 标点禁则（行首不能出现「，。！？」等）。这里按日文排版的「追い出し」处理：
        //   - 挤得下：把标点拉进本行；
        //   - 挤不下：把标点连同它前面那个字符一起推到下一行。
        // 关键是绝不能让本行超宽——超宽的气泡会被屏幕裁掉，文字就「被吞了」。
        while (k < n && isNoStart(text.charAt(k))) {
            int next = k + 1;
            if (w.of(text, from, next) <= maxWidth) {
                k = next;
            } else if (k > from + 1) {
                k = k - 1;
            }
            break;
        }
        // 兜底：断点回退、标点拉取都可能留下超宽的行，这里强制收到预算内。
        if (w.of(text, from, k) > maxWidth) k = fitEnd(text, from, maxWidth, w);
        return k;
    }

    /**
     * 从 from 起最多能放到哪个下标（不含）而不超过 maxWidth。
     * 至少放一个字符——单个字符比 maxWidth 还宽时也只能让它单独占一行。
     */
    private static int fitEnd(String text, int from, float maxWidth, Width w) {
        int n = text.length();
        if (from >= n) return n;
        if (w.of(text, from, n) <= maxWidth) return n;
        int lo = from + 1, hi = n;      // 二分：找满足宽度限制的最大下标
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (w.of(text, from, mid) <= maxWidth) lo = mid;
            else hi = mid - 1;
        }
        return lo;
    }

    /** 从 pos 开始的 token 到哪里结束。 */
    private static int nextTokenEnd(String text, int pos) {
        char c = text.charAt(pos);
        if (c == ' ') return pos + 1;
        if (isCjk(c)) return pos + 1;
        int i = pos;
        while (i < text.length()) {
            char x = text.charAt(i);
            if (x == ' ' || isCjk(x)) break;
            i++;
        }
        return i;
    }

    /** pos 处是否可以断行（pos 是下一行的起始位置）。 */
    private static boolean breakableAfter(String text, int pos) {
        if (pos >= text.length()) return true;
        char prev = text.charAt(pos - 1);
        char next = text.charAt(pos);
        if (prev == ' ') return true;
        if (isCjk(next)) return true;   // 汉字之间随处可断
        if (isCjk(prev)) return true;   // 中文后面可以断（万一断在标点前，lineEnd 会把标点拉回来）
        return false;                   // 拉丁词中间不断
    }

    private static boolean isNoStart(char c) {
        return NO_START.indexOf(c) >= 0;
    }

    static boolean isCjk(char c) {
        return (c >= 0x2E80 && c <= 0x303F)     // 部首、CJK 标点
                || (c >= 0x3040 && c <= 0x30FF)  // 假名
                || (c >= 0x3400 && c <= 0x9FFF)  // 汉字
                || (c >= 0xAC00 && c <= 0xD7AF)  // 谚文
                || (c >= 0xF900 && c <= 0xFAFF); // 兼容汉字
    }

    private static String ltrim(String s) {
        int b = 0;
        while (b < s.length() && s.charAt(b) == ' ') b++;
        return b == 0 ? s : s.substring(b);
    }

    private static String rtrim(String s) {
        int e = s.length();
        while (e > 0 && s.charAt(e - 1) == ' ') e--;
        return e == s.length() ? s : s.substring(0, e);
    }
}

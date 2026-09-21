package com.coco.balancebubble;

import java.util.ArrayList;
import java.util.List;

/**
 * 中英混排断行：中日韩字符逐字断，拉丁字母数字按词断，标点不落在行首。
 *
 * <p>写成不依赖 Android 的纯逻辑类，是为了能在 JVM 上直接跑单测
 * （沙箱里没有 Android 运行时，{@code StaticLayout} 跑不起来）。
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
        List<String> out = new ArrayList<String>();
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
        return out;
    }

    /** 从 from 开始，本行最多能放到哪个下标（不含）。 */
    private static int lineEnd(String text, int from, float maxWidth, Width w) {
        int n = text.length();
        int k = from;
        float used = 0;
        int lastBreak = -1;
        while (k < n) {
            int tokEnd = nextTokenEnd(text, k);
            float tw = w.of(text, k, tokEnd);
            if (k > from && used + tw > maxWidth) break;
            used += tw;
            k = tokEnd;
            if (breakableAfter(text, k)) lastBreak = k;
        }
        if (k < n && lastBreak > from) k = lastBreak;
        // 断点正好落在标点前：把标点拉进本行，避免行首出现「，。」之类。
        while (k < n && isNoStart(text.charAt(k))) k++;
        return k;
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

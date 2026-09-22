package com.coco.balancebubble;

/**
 * 版本号的解析与比较。
 *
 * <p>把 GitHub release 的 tag（例如 {@code v1.7}）抠成纯数字版本号（{@code 1.7}），
 * 再按小数点分段比大小。全是字符串运算：不碰网络、不碰 Android，所以能在 JVM 上
 * 直接跑单测（{@code test/VersionTest.java}）。
 */
public final class Version {

    private Version() {
    }

    /** 从 tag 抠出版本号："v1.7" → "1.7"，"1.7-rc1" → "1.7"；认不出返回空串。 */
    public static String of(String tag) {
        if (tag == null) return "";
        String s = tag.trim();
        if (s.length() > 0 && (s.charAt(0) == 'v' || s.charAt(0) == 'V')) s = s.substring(1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= '0' && ch <= '9') {
                sb.append(ch);
            } else if (ch == '.' && sb.length() > 0 && sb.charAt(sb.length() - 1) != '.') {
                sb.append('.');
            } else {
                break;
            }
        }
        while (sb.length() > 0 && sb.charAt(sb.length() - 1) == '.') {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    /**
     * a 比 b 新吗？按小数点分段比数字，段数不齐时缺的算 0。
     * 所以 1.10 比 1.9 新、2.0 比 1.9.9 新、1.7 不比 1.7 新。
     *
     * <p>两边任一抠不出版本号（例如本地 versionName 是空串）就一律返回 false：
     * 宁可漏报，也不要凭空弹一个「有新版本」。
     */
    public static boolean isNewer(String a, String b) {
        String[] x = segments(of(a));
        String[] y = segments(of(b));
        if (x.length == 0 || y.length == 0) return false;
        int n = Math.max(x.length, y.length);
        for (int i = 0; i < n; i++) {
            int xi = i < x.length ? number(x[i]) : 0;
            int yi = i < y.length ? number(y[i]) : 0;
            if (xi != yi) return xi > yi;
        }
        return false;
    }

    private static String[] segments(String v) {
        if (v.length() == 0) return new String[0];
        return v.split("\\.");
    }

    /** 段内数字；非数字开头算 0，超过一百万就封顶（防溢出）。 */
    private static int number(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch < '0' || ch > '9') break;
            n = n * 10 + (ch - '0');
            if (n > 1000000) break;
        }
        return n;
    }
}

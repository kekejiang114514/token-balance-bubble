import com.coco.balancebubble.Version;

/**
 * JVM 单测：tag 解析 + 版本比较。
 *
 * <p>这两件事决定「要不要弹更新提示」，写错的两个方向都很难受：比大了天天弹一个
 * 装不上的版本，比小了永远不提醒。所以边界值挨个钉一遍。
 */
public class VersionTest {
    static int fail = 0;
    static int checks = 0;

    static void eq(String what, Object a, Object b) {
        checks++;
        boolean ok = a == null ? b == null : a.equals(b);
        if (!ok) {
            fail++;
            System.out.println("  FAIL " + what + " 期望=" + b + " 实际=" + a);
        }
    }

    static void ok(String what, boolean cond) {
        checks++;
        if (!cond) {
            fail++;
            System.out.println("  FAIL " + what);
        }
    }

    public static void main(String[] args) {
        System.out.println("== tag 解析 ==");
        eq("v 前缀", Version.of("v1.7"), "1.7");
        eq("大写 V 前缀", Version.of("V2.0"), "2.0");
        eq("没有前缀", Version.of("1.7"), "1.7");
        eq("去掉预发布后缀", Version.of("1.7-rc1"), "1.7");
        eq("去掉 +build", Version.of("v1.7+build3"), "1.7");
        eq("去掉空格", Version.of("  v1.7  "), "1.7");
        eq("两位小数", Version.of("v1.10.3"), "1.10.3");
        eq("末尾小数点要削掉", Version.of("v1."), "1");
        eq("连续小数点处截断（第二个点即停）", Version.of("v1..2"), "1");
        eq("以点开头直接断", Version.of(".1"), "");
        eq("空 tag", Version.of(""), "");
        eq("null tag", Version.of(null), "");
        eq("只有 v", Version.of("v"), "");
        eq("纯文字", Version.of("nightly"), "");
        eq("文字开头", Version.of("release-1.7"), "");

        System.out.println("== 版本比较：新的 ==");
        ok("1.7 > 1.6", Version.isNewer("1.7", "1.6"));
        ok("1.10 > 1.9（按数字比，不是按字符串）", Version.isNewer("1.10", "1.9"));
        ok("2.0 > 1.9.9", Version.isNewer("2.0", "1.9.9"));
        ok("1.7.1 > 1.7", Version.isNewer("1.7.1", "1.7"));
        ok("1.7.0 不比 1.7 新，但 1.7.1 比 1.7.0 新", Version.isNewer("1.7.1", "1.7.0"));
        ok("v 前缀也认", Version.isNewer("v1.8", "1.7"));
        ok("两边都带 v", Version.isNewer("v1.8", "v1.7"));
        ok("10.0 > 9.9", Version.isNewer("10.0", "9.9"));

        System.out.println("== 版本比较：不是新的 ==");
        ok("相等不比新", !Version.isNewer("1.7", "1.7"));
        ok("1.7 不比 1.7.0 新（缺段算 0）", !Version.isNewer("1.7", "1.7.0"));
        ok("旧的", !Version.isNewer("1.6", "1.7"));
        ok("1.9 不比 1.10 新", !Version.isNewer("1.9", "1.10"));
        ok("本地更新也不弹（相等）", !Version.isNewer("v1.7", "1.7"));

        System.out.println("== 版本比较：认不出来一律 false（宁漏勿误）==");
        ok("远端空", !Version.isNewer("", "1.7"));
        ok("本地空", !Version.isNewer("1.7", ""));
        ok("两边都空", !Version.isNewer("", ""));
        ok("远端 null", !Version.isNewer(null, "1.7"));
        ok("本地 null", !Version.isNewer("1.7", null));
        ok("远端是垃圾", !Version.isNewer("nightly", "1.7"));
        ok("本地是垃圾", !Version.isNewer("1.7", "dev"));

        System.out.println("== 防溢出 ==");
        ok("超长数字不炸且仍能比较", Version.isNewer("99999999999.0", "1.0"));
        ok("超长数字不会溢出成负数", !Version.isNewer("1.0", "99999999999.0"));

        System.out.println("== 单调性抽查 ==");
        String[] chain = {"0.9", "1.0", "1.3", "1.7", "1.7.1", "1.8", "1.10", "2.0", "2.0.1", "10.0"};
        for (int i = 0; i + 1 < chain.length; i++) {
            ok(chain[i + 1] + " > " + chain[i], Version.isNewer(chain[i + 1], chain[i]));
            ok(chain[i] + " 不 > " + chain[i + 1], !Version.isNewer(chain[i], chain[i + 1]));
        }
        for (int i = 0; i < chain.length; i++) {
            ok(chain[i] + " 不比自身新", !Version.isNewer(chain[i], chain[i]));
        }

        System.out.println(fail == 0
                ? "VersionTest 通过（" + checks + " 项断言）"
                : "VersionTest 失败 " + fail + "/" + checks);
        if (fail != 0) System.exit(1);
    }
}

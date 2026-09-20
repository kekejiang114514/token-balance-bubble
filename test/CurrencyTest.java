import com.coco.balancebubble.Currencies;
import com.coco.balancebubble.Presets;

/** JVM 单测：币种表 + 服务商预设的一致性。 */
public class CurrencyTest {
    static int fail = 0;

    static void eq(String what, Object a, Object b) {
        boolean ok = a == null ? b == null : a.equals(b);
        if (!ok) {
            fail++;
            System.out.println("  FAIL " + what + " 期望=" + b + " 实际=" + a);
        }
    }

    static void ok(String what, boolean cond) {
        if (!cond) {
            fail++;
            System.out.println("  FAIL " + what);
        }
    }

    public static void main(String[] args) {
        System.out.println("== 币种表 ==");
        for (int i = 0; i < Currencies.CODES.length; i++) {
            System.out.printf("  [%2d] %-18s code=%-8s symbol=%s%n",
                    i, Currencies.NAMES[i], Currencies.CODES[i], Currencies.SYMBOLS[i]);
        }
        eq("默认币种是人民币", Currencies.CODES[Currencies.DEFAULT_INDEX], "CNY");
        eq("默认符号是 ¥", Currencies.SYMBOLS[Currencies.DEFAULT_INDEX], "\u00a5");
        eq("人民币展示名", Currencies.label(0), "人民币 CNY \u00a5");
        ok("每个币种都有展示名", Currencies.label(Currencies.CODES.length - 1).length() > 4);

        System.out.println("== 明确选择币种 ==");
        eq("USD 前缀", Currencies.prefix("USD", "", "CNY", false), "$");
        eq("CNY 前缀", Currencies.prefix("CNY", "", "USD", false), "\u00a5");
        eq("JPY 前缀", Currencies.prefix("JPY", "", "", false), "JP\u00a5");
        eq("EUR 前缀", Currencies.prefix("EUR", "", "USD", false), "\u20ac");

        System.out.println("== 自动识别 ==");
        eq("自动+CNY", Currencies.prefix(Currencies.AUTO, "", "CNY", false), "\u00a5");
        eq("自动+rmb 小写", Currencies.prefix(Currencies.AUTO, "", "rmb", false), "\u00a5");
        eq("自动+usd 小写", Currencies.prefix(Currencies.AUTO, "", "usd", false), "$");
        eq("自动+未知代码原样显示", Currencies.prefix(Currencies.AUTO, "", "XYZ", false), "XYZ");
        eq("自动+无币种留空", Currencies.prefix(Currencies.AUTO, "", "", false), "");

        System.out.println("== 自定义符号 ==");
        eq("自定义", Currencies.prefix(Currencies.CUSTOM, "积分", "", false), "积分");
        eq("自定义留空", Currencies.prefix(Currencies.CUSTOM, "", "CNY", false), "");

        System.out.println("== 金额后缀 ==");
        eq("后缀关", Currencies.suffix("CNY", false), "");
        eq("后缀开", Currencies.suffix("CNY", true), " CNY");
        eq("自动识别不加后缀", Currencies.suffix(Currencies.AUTO, true), "");
        eq("自定义不加后缀", Currencies.suffix(Currencies.CUSTOM, true), "");
        eq("无币种不加后缀", Currencies.suffix("", true), "");

        System.out.println("== 索引解析 ==");
        eq("indexOfCode(CNY)", Currencies.indexOfCode("CNY"), 0);
        eq("indexOfCode(usd) 忽略大小写", Currencies.indexOfCode("usd"), Currencies.indexOf("USD"));
        eq("indexOfCode(空) 回落默认", Currencies.indexOfCode(""), Currencies.DEFAULT_INDEX);
        eq("indexOfCode(乱填) 归自定义", Currencies.indexOfCode("积分"), Currencies.indexOf(Currencies.CUSTOM));
        ok("isAuto", Currencies.isAuto(Currencies.indexOf(Currencies.AUTO)));
        ok("isCustom", Currencies.isCustom(Currencies.indexOf(Currencies.CUSTOM)));
        ok("CNY 既非自动也非自定义",
                !Currencies.isAuto(0) && !Currencies.isCustom(0));

        System.out.println("== 服务商预设 ==");
        for (int i = 0; i < Presets.NAMES.length; i++) {
            String code = Presets.currency(i);
            if (!code.isEmpty()) {
                ok(Presets.NAMES[i] + " 的币种 " + code + " 不在币种表里",
                        Currencies.indexOf(code) >= 0);
            }
            if (!Presets.isCustom(i)) {
                ok(Presets.NAMES[i] + " 缺少 base", !Presets.base(i).isEmpty());
                ok(Presets.NAMES[i] + " 缺少 path", !Presets.path(i).isEmpty());
                ok(Presets.NAMES[i] + " 缺少 label", !Presets.label(i).isEmpty());
                ok(Presets.NAMES[i] + " 缺少说明", !Presets.note(i).isEmpty());
            }
            System.out.printf("  %-10s %-24s %-26s %-30s %-4s %s%n",
                    Presets.NAMES[i], Presets.base(i), Presets.path(i),
                    Presets.extract(i), code, Presets.note(i));
        }
        eq("indexOfName(DeepSeek)", Presets.indexOfName("DeepSeek"), 0);
        eq("indexOfName(不认识) 归自定义", Presets.indexOfName("xx"), Presets.CUSTOM_INDEX);
        eq("indexOfName(空) 归自定义", Presets.indexOfName(""), Presets.CUSTOM_INDEX);
        eq("OpenAI 预设币种 USD", Presets.currency(4), "USD");
        eq("DeepSeek 预设币种 CNY", Presets.currency(0), "CNY");

        System.out.println(fail == 0 ? "\n全部通过" : "\n失败 " + fail + " 项");
        if (fail > 0) System.exit(1);
    }
}

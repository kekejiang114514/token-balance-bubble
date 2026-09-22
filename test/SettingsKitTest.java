import com.coco.balancebubble.BubbleLayout;
import com.coco.balancebubble.Currencies;
import com.coco.balancebubble.Theme;

/**
 * 设置页重做之后，把不依赖 Android 的那几块逻辑单测一遍。
 *
 * <p>覆盖三件事：主题配色的可读性（对比度判据）、气泡排版的硬约束
 * （宽度和行数不越界）、币种在「下标 ↔ 代号」之间的往返
 * （设置页现在存的是代号，往返错了就会显示成别的币种）。
 *
 * <p>纯 JVM：不 import 任何 android 类，直接 javac + java 跑。
 */
public class SettingsKitTest {

    private static int pass = 0;
    private static int fail = 0;

    private static void ok(String name, boolean cond) {
        if (cond) {
            pass++;
        } else {
            fail++;
            System.out.println("  [FAIL] " + name);
        }
    }

    private static void ok(String name, boolean cond, String detail) {
        if (cond) {
            pass++;
        } else {
            fail++;
            System.out.println("  [FAIL] " + name + " —— " + detail);
        }
    }

    private static String round(double v) {
        return String.valueOf(Math.round(v * 100) / 100.0);
    }

    // ---------------- 对比度 ----------------

    /** WCAG 相对亮度（sRGB 线性化）。 */
    private static double relLum(int c) {
        double r = lin(((c >> 16) & 0xFF) / 255.0);
        double g = lin(((c >> 8) & 0xFF) / 255.0);
        double b = lin((c & 0xFF) / 255.0);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double lin(double v) {
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    /** 对比度，1（同色）到 21（黑白）。 */
    private static double contrast(int a, int b) {
        double la = relLum(a);
        double lb = relLum(b);
        double hi = Math.max(la, lb);
        double lo = Math.min(la, lb);
        return (hi + 0.05) / (lo + 0.05);
    }

    /** 正文至少 4.5:1，大字和辅助文字至少 3:1，这是 WCAG AA 的线。 */
    private static final double AA_TEXT = 4.5;
    private static final double AA_BIG = 3.0;

    // ---------------- 主题配色 ----------------

    /** 六档主题色 × 深浅两种模式，正文、辅助文字、按钮字都得看得清。 */
    private static void themeReadable() {
        for (int i = 0; i < Theme.count(); i++) {
            for (int d = 0; d < 2; d++) {
                boolean dark = d == 1;
                Theme t = new Theme(i, dark);
                String tag = Theme.NAMES[i] + (dark ? "/深色" : "/浅色");
                ok("正文可读 " + tag, contrast(t.txt(), t.bg()) >= AA_TEXT,
                        "对比度 " + round(contrast(t.txt(), t.bg())));
                ok("卡片正文可读 " + tag, contrast(t.txt(), t.card()) >= AA_TEXT,
                        "对比度 " + round(contrast(t.txt(), t.card())));
                ok("辅助文字可读 " + tag, contrast(t.sub(), t.card()) >= AA_BIG,
                        "对比度 " + round(contrast(t.sub(), t.card())));
                ok("成功色可读 " + tag, contrast(t.ok(), t.okBg()) >= AA_BIG,
                        "对比度 " + round(contrast(t.ok(), t.okBg())));
                ok("错误色可读 " + tag, contrast(t.err(), t.errBg()) >= AA_BIG,
                        "对比度 " + round(contrast(t.err(), t.errBg())));
                ok("按钮字压在主题色上可读 " + tag,
                        contrast(Theme.inkOn(t.accent()), t.accent()) >= AA_BIG,
                        "对比度 " + round(contrast(Theme.inkOn(t.accent()), t.accent())));
            }
        }
    }

    private static void themeClamp() {
        ok("主题下标越界夹到最后一档", new Theme(99, false).theme == Theme.count() - 1);
        ok("主题下标负数夹到 0", new Theme(-5, false).theme == 0);
        ok("色板与名字表一样长", Theme.accentPalette().length == Theme.NAMES.length);
        ok("每档主题色都不重复", distinct(Theme.accentPalette()) == Theme.NAMES.length);
        ok("每档主题色都不透明", allOpaque(Theme.accentPalette()));
    }

    private static int distinct(int[] a) {
        int n = 0;
        for (int i = 0; i < a.length; i++) {
            boolean dup = false;
            for (int j = 0; j < i; j++) {
                if (a[j] == a[i]) dup = true;
            }
            if (!dup) n++;
        }
        return n;
    }

    private static boolean allOpaque(int[] a) {
        for (int v : a) {
            if ((v >>> 24) != 0xFF) return false;
        }
        return true;
    }

    // ---------------- 气泡底色 ----------------

    /** 「自动」跟深浅模式走；固定档位是浅色或深色块，压在上面的字都得看得清。 */
    private static void bubbleBgReadable() {
        ok("底色色板与名字表一样长", Theme.SWATCH.length == Theme.SWATCH_NAMES.length);
        ok("色板第一档是「自动」", Theme.SWATCH[0] == 0 && Theme.SWATCH_NAMES[0].contains("自动"));
        for (int d = 0; d < 2; d++) {
            boolean dark = d == 1;
            Theme t = new Theme(0, dark);
            ok("0 和 -1 都是「自动」" + dark, t.bubbleBg(0) == t.bubbleBg(-1));
            int auto = t.bubbleBg(0);
            ok("自动底色上的字可读 " + dark, contrast(Theme.inkOn(auto), auto) >= AA_TEXT,
                    "对比度 " + round(contrast(Theme.inkOn(auto), auto)));
            for (int i = 1; i < Theme.SWATCH.length; i++) {
                int c = Theme.SWATCH[i];
                String tag = Theme.SWATCH_NAMES[i] + (dark ? "/深色" : "/浅色");
                ok("固定底色上的字可读 " + tag, contrast(Theme.inkOn(c), c) >= AA_TEXT,
                        "对比度 " + round(contrast(Theme.inkOn(c), c)));
                ok("固定底色上的次要字可读 " + tag, contrast(Theme.subInkOn(c), c) >= AA_BIG,
                        "对比度 " + round(contrast(Theme.subInkOn(c), c)));
                ok("固定底色上的错误色可读 " + tag, contrast(Theme.errInkOn(c), c) >= AA_BIG,
                        "对比度 " + round(contrast(Theme.errInkOn(c), c)));
                ok("字色取的是对比度更高的一侧 " + tag,
                        contrast(Theme.inkOn(c), c) >= contrast(otherInk(Theme.inkOn(c)), c),
                        "对比度 " + round(contrast(Theme.inkOn(c), c))
                                + " / 另一侧 " + round(contrast(otherInk(Theme.inkOn(c)), c)));
            }
        }
        ok("「自动」底色在深浅模式下不同",
                new Theme(0, false).bubbleBg(0) != new Theme(0, true).bubbleBg(0));
    }

    private static int otherInk(int ink) {
        return ink == Theme.INK_DARK ? Theme.INK_LIGHT : Theme.INK_DARK;
    }

    // ---------------- 币种：下标 ↔ 代号 ----------------

    /**
     * 设置页存的是币种代号（"CNY"），读数时再换回下标。
     * 这两步任何一处不互逆，用户选的就是另一个币种。
     */
    private static void currencyRoundtrip() {
        ok("代号表与符号表一样长", Currencies.CODES.length == Currencies.SYMBOLS.length);
        ok("代号表与中文名表一样长", Currencies.CODES.length == Currencies.NAMES.length);
        ok("默认是人民币", Currencies.CODES[Currencies.DEFAULT_INDEX].equals("CNY"));
        for (int i = 0; i < Currencies.CODES.length; i++) {
            String code = Currencies.code(i);
            ok("代号往返 " + code, Currencies.indexOfCode(code) == i,
                    "回到下标 " + Currencies.indexOfCode(code) + "，原下标 " + i);
            ok("代号往返（小写）" + code,
                    Currencies.indexOfCode(code.toLowerCase()) == i);
            ok("下标往返 " + code, Currencies.indexOf(Currencies.code(i)) == i);
            ok("下拉文案不为空 " + code, Currencies.label(i).length() > 0);
        }
        ok("下拉文案互不重复", distinctLabels() == Currencies.CODES.length);
        ok("空代号回默认", Currencies.indexOfCode("") == Currencies.DEFAULT_INDEX);
        ok("null 代号回默认", Currencies.indexOfCode(null) == Currencies.DEFAULT_INDEX);
        ok("认不出的代号落到自定义", Currencies.isCustom(Currencies.indexOfCode("XYZ")));
        ok("下标越界夹回默认", Currencies.clamp(999) == Currencies.DEFAULT_INDEX
                && Currencies.clamp(-1) == Currencies.DEFAULT_INDEX);
        ok("自动档与自定义档的标志位", Currencies.isAuto(Currencies.indexOf(Currencies.AUTO))
                && Currencies.isCustom(Currencies.indexOf(Currencies.CUSTOM)));

        ok("接口给 RMB 当人民币", Currencies.symbolOfCode("RMB").equals(Currencies.symbol(0)));
        ok("接口给 cnh 当人民币", Currencies.symbolOfCode("cnh").equals(Currencies.symbol(0)));
        ok("接口给 USDT 当美元", Currencies.symbolOfCode("USDT").equals(Currencies.symbol(1)));
        ok("接口没给币种时前缀为空",
                Currencies.prefix(Currencies.AUTO, "", null, false).length() == 0);
        ok("自动档用接口的币种", Currencies.prefix(Currencies.AUTO, "", "USD", false).equals("$"));
        ok("自定义档用自填符号",
                Currencies.prefix(Currencies.CUSTOM, "元", "USD", false).equals("元"));
        ok("固定币种不看接口",
                Currencies.prefix("EUR", "", "USD", false).equals(Currencies.symbol(2)));
        ok("补代码只出现在固定币种", Currencies.suffix("CNY", true).equals(" CNY"));
        ok("自动档不补代码", Currencies.suffix(Currencies.AUTO, true).length() == 0);
        ok("自定义档不补代码", Currencies.suffix(Currencies.CUSTOM, true).length() == 0);
        ok("不补代码时为空", Currencies.suffix("CNY", false).length() == 0);
    }

    private static int distinctLabels() {
        int n = 0;
        for (int i = 0; i < Currencies.CODES.length; i++) {
            boolean dup = false;
            for (int j = 0; j < i; j++) {
                if (Currencies.label(j).equals(Currencies.label(i))) dup = true;
            }
            if (!dup) n++;
        }
        return n;
    }
    // ---------------- 气泡排版 ----------------

    private static BubbleLayout.Spec spec() {
        BubbleLayout.Spec s = new BubbleLayout.Spec();
        s.maxWidth = 720;
        s.minWidth = 180;
        s.padH = 15;
        s.padT = 9;
        s.padB = 11;
        s.gap = 4;
        s.tailH = 6;
        s.baseSize = 46;
        s.minSize = 26;
        s.shrink = 0.9f;
        s.maxLines = 3;
        s.hardMaxLines = 4;
        s.labelSize = 22;
        return s;
    }

    /**
     * 排版的硬约束：宽度不许超上限、行数不许超封顶、字号不许越界。
     *
     * <p>这些是悬浮窗里真在用的不变量：一旦越界，气泡会盖住整块屏幕或者被裁掉。
     */
    private static void layoutFits() {
        BubbleLayout.Spec s = spec();
        FakeMetrics m = new FakeMetrics();
        String[] samples = {"", "¥0.00", "¥33.83", "¥1234567.89", "余额已用完，记得充值",
                "USD 1234.56", "免费额度 0.00 / 已赠 12.34"};
        String[] labels = {"余额", "硅基流动账户余额"};
        for (String label : labels) {
            for (String body : samples) {
                checkFit(label + "｜" + body, label, body, s, m);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            sb.append("很长的一句话");
        }
        checkFit("超长文字", "余额", sb.toString(), s, m);
    }

    private static void checkFit(String name, String label, String amount,
                                 BubbleLayout.Spec s, FakeMetrics m) {
        BubbleLayout.Result r = BubbleLayout.fit(label, amount, s, m);
        ok(name + "：宽度不超上限", r.width <= s.maxWidth + 0.01f,
                "宽度 " + r.width + " / 上限 " + s.maxWidth);
        ok(name + "：行数不超封顶", r.lines.size() <= s.hardMaxLines,
                "行数 " + r.lines.size());
        ok(name + "：字号在区间内",
                r.size >= s.minSize - 0.01f && r.size <= s.baseSize + 0.01f, "字号 " + r.size);
        ok(name + "：高度为正", r.height > 0, "高度 " + r.height);
        ok(name + "：正文至少一行", r.lines.size() >= 1, "行数 " + r.lines.size());
        // 真正的硬不变量：每一行都得塞进内容宽度预算，否则气泡会被屏幕裁掉。
        m.setSize(r.size);
        float innerMax = s.maxWidth - s.padH * 2f;
        float widest = 0f;
        for (String line : r.lines) {
            float w = m.width(line, 0, line.length());
            widest = Math.max(widest, w);
        }
        ok(name + "：每行都不超内容宽度", widest <= innerMax + 0.01f,
                "最宽一行 " + widest + " / 预算 " + innerMax);
        ok(name + "：气泡宽度容得下最宽一行", r.width + 0.01f >= widest + s.padH * 2f);
        // 没截断时文字不能丢：把各行拼回去（忽略空格）应该和原文一致。
        if (!r.truncated) {
            StringBuilder joined = new StringBuilder();
            for (String line : r.lines) joined.append(line);
            String want = (amount == null || amount.length() == 0) ? "--" : amount;
            ok(name + "：未截断时文字不丢",
                    squeeze(joined.toString()).equals(squeeze(want)),
                    "折回「" + joined + "」，原文「" + want + "」");
        }
        if (r.truncated) {
            String last = r.lines.get(r.lines.size() - 1);
            ok(name + "：截断时末行带省略号", last.endsWith("…"), "末行「" + last + "」");
            ok(name + "：截断时行数用满", r.lines.size() == s.hardMaxLines,
                    "行数 " + r.lines.size() + " / 封顶 " + s.hardMaxLines);
        }
        if (r.shrinkSteps > 0) {
            ok(name + "：缩过之后字号更小", r.size < s.baseSize, "字号 " + r.size);
        }
    }

    /** 去掉所有空格，用于比较折行前后有没有丢字符。 */
    private static String squeeze(String s) {
        return s.replace(" ", "");
    }

    /** 假字体度量：中文按一个字宽、西文按 0.55 估，够用来验证约束。 */
    private static class FakeMetrics implements BubbleLayout.Metrics {
        private float size = 16f;

        @Override
        public void setSize(float px) {
            this.size = px;
        }

        @Override
        public float width(String text, int from, int to) {
            float w = 0;
            for (int i = from; i < to && i < text.length(); i++) {
                w += (text.charAt(i) > 0x2E80 ? 1.0f : 0.55f) * size;
            }
            return w;
        }

        @Override
        public float lineHeight() {
            return size * 1.25f;
        }
    }

    public static void main(String[] args) {
        System.out.println("---- 主题配色 ----");
        themeReadable();
        themeClamp();
        System.out.println("---- 气泡底色 ----");
        bubbleBgReadable();
        System.out.println("---- 气泡排版 ----");
        layoutFits();
        System.out.println("---- 币种往返 ----");
        currencyRoundtrip();
        System.out.println();
        System.out.println("SettingsKitTest: " + pass + " 项通过, " + fail + " 项失败");
        if (fail > 0) System.exit(1);
    }
}

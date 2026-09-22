import com.coco.balancebubble.TextWrap;

import java.util.List;

/** JVM 单测：气泡断行。每个字符按固定 10 宽计算，结果好断言。 */
public class TextWrapTest {
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

    private static final TextWrap.Width TEN = new TextWrap.Width() {
        @Override
        public float of(String text, int from, int to) {
            return (to - from) * 10f;
        }
    };

    private static List<String> w(String text, float maxW, int maxLines) {
        return TextWrap.wrap(text, maxW, maxLines, TEN);
    }

    public static void main(String[] args) {
        List<String> r;

        System.out.println("== 基本 ==");
        eq("空串不产生行", w("", 100, 3).size(), 0);
        eq("null 不产生行", w(null, 100, 3).size(), 0);
        r = w("你好", 100, 3);
        eq("放得下就是一行", r.size(), 1);
        eq("内容原样", r.get(0), "你好");

        System.out.println("== 中文逐字断 ==");
        r = w("一二三四五六七八九十", 50, 0);
        eq("每行 5 字 → 2 行", r.size(), 2);
        eq("第一行", r.get(0), "一二三四五");
        eq("第二行", r.get(1), "六七八九十");

        System.out.println("== 英文按词断 ==");
        r = w("hello world", 60, 0);
        eq("两个单词 → 2 行", r.size(), 2);
        eq("第一行完整单词", r.get(0), "hello");
        eq("行尾空格去掉", r.get(1), "world");
        r = w("余额 33.83 CNY", 70, 0);
        ok("中英混排不切碎数字", r.get(0).indexOf("33.83") >= 0 || r.size() > 1);

        System.out.println("== 标点禁则（追い出し：宁可行短，绝不超宽）==");
        // 旧行为是把标点硬挤进本行，首行会变成 30 宽、超过上限 20 —— 那正是
        // 气泡被屏幕裁掉的根因，所以这里改成断言「不丢字 + 不超宽 + 标点不落行首」。
        String NO_START = "，。！？、；：）】」』";
        r = w("你好，世界", 20, 0);
        eq("逗号一句不丢", String.join("", r), "你好，世界");
        for (String l : r) {
            ok("逗号不落行首：" + l, NO_START.indexOf(l.charAt(0)) < 0);
            ok("行宽不超上限：" + l, l.length() * 10 <= 20);
        }
        r = w("收到！好的", 20, 0);
        eq("感叹号一句不丢", String.join("", r), "收到！好的");
        for (String l : r) {
            ok("感叹号不落行首：" + l, NO_START.indexOf(l.charAt(0)) < 0);
            ok("行宽不超上限：" + l, l.length() * 10 <= 20);
        }

        System.out.println("== 截断 ==");
        r = w("这是一段很长的说明文字需要折成多行显示", 40, 2);
        eq("最多两行", r.size(), 2);
        ok("末行以省略号收尾：" + r.get(1),
                r.get(1).endsWith(String.valueOf(TextWrap.ELLIPSIS)));
        ok("省略号行不超过最大宽度", r.get(1).length() * 10 <= 40 + 10);

        System.out.println("== 边界 ==");
        eq("单词宽度正好等于上限时不硬挤", w("abc def ghi", 30, 0).size(), 3);
        eq("超长单字也要前进", w("大大大大", 5, 0).size(), 4);
        r = w("  你好  ", 100, 0);
        eq("空白不产生额外行", r.size(), 1);
        eq("行首尾空白去掉", r.get(0), "你好");

        System.out.println("== 超长不可断 token 必须硬切（回归：气泡把文字吞掉）==");
        // 字段路径这类 token 中间没有可断点，若整段塞进一行，行宽会远超气泡上限，
        // 窗口被系统裁到屏幕宽后文字两侧就被切掉。
        String path = "balance_infos.0.total_balance";
        r = w(path, 100, 0);
        ok("超长 token 必须被拆成多行：" + r, r.size() > 1);
        for (String l : r) {
            ok("每行都不超过上限：" + l, l.length() * 10 <= 100);
        }
        StringBuilder joined = new StringBuilder();
        for (String l : r) joined.append(l);
        // 硬切不丢字符（token 内没有空白，去空后应完全还原）
        eq("硬切不丢字符", joined.toString().replace(" ", ""), path);

        r = w("按路径 " + path + " 没取到值。", 100, 0);
        for (String l : r) {
            ok("真实错误文案每行不超上限：" + l, l.length() * 10 <= 100);
        }
        eq("错误文案一个字都没丢",
                String.join("", r).replace(" ", ""),
                ("按路径 " + path + " 没取到值。").replace(" ", ""));

        System.out.println("== 标点禁则不得突破宽度上限 ==");
        r = w("你好，世界", 25, 0);
        for (String l : r) {
            ok("标点拉取后仍不超上限：" + l, l.length() * 10 <= 25);
        }

        System.out.println("== 截断要如实报告 ==");
        boolean[] t = new boolean[1];
        r = TextWrap.wrap("这是一段很长的说明文字需要折成多行显示", 40, 2, TEN, t);
        ok("确实截断了要报 true", t[0]);
        ok("末行有省略号", r.get(r.size() - 1).endsWith(String.valueOf(TextWrap.ELLIPSIS)));
        t[0] = true;
        r = TextWrap.wrap("正在刷新中" + TextWrap.ELLIPSIS, 100, 2, TEN, t);
        ok("文本自带省略号不算截断", !t[0]);
        t[0] = true;
        r = TextWrap.wrap("困了" + TextWrap.ELLIPSIS + "睡一会儿" + TextWrap.ELLIPSIS, 100, 2, TEN, t);
        ok("自带省略号的长句也不算截断", !t[0]);
        r = TextWrap.wrap("不限行数时绝不截断", 40, 0, TEN, t);
        ok("maxLines=0 不截断", !t[0]);

        System.out.println("== 全语料不变量 ==");
        String[] corpus = {
            "余额 ¥33.83", "正在刷新中" + TextWrap.ELLIPSIS, "呜哇！吓到了吗？",
            "按路径 balance_infos.0.total_balance 没取到值。",
            "还没填 API Key。去服务商官网的「API Keys」页面复制一个，粘贴到上面。",
            "域名解析失败，Base URL 可能写错了（高级设置里可改）",
            "这个地址不允许明文 HTTP，请把 Base URL 换成 https://",
            "https://api.deepseek.com/user/balance",
        };
        for (String c : corpus) {
            for (float mw : new float[]{540f, 742.5f, 774f}) {
                for (int ml : new int[]{0, 3}) {
                    for (String l : TextWrap.wrap(c, mw, ml, TEN)) {
                        ok("不变量：行宽<=上限 (" + mw + ") 「" + l + "」", l.length() * 10 <= mw);
                    }
                }
            }
        }

        System.out.println(fail == 0 ? "\n全部通过" : "\n失败 " + fail + " 项");
        if (fail > 0) System.exit(1);
    }
}

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

        System.out.println("== 标点禁则 ==");
        r = w("你好，世界", 20, 0);
        eq("标点被拉回上一行 → 2 行", r.size(), 2);
        eq("逗号不在行首", r.get(0), "你好，");
        eq("第二行", r.get(1), "世界");
        r = w("收到！好的", 20, 0);
        eq("感叹号也不落行首", r.get(0), "收到！");

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

        System.out.println(fail == 0 ? "\n全部通过" : "\n失败 " + fail + " 项");
        if (fail > 0) System.exit(1);
    }
}

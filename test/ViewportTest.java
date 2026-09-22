import com.coco.balancebubble.Viewport;

/**
 * 视口算术单测。这些断言对着的就是用户报的那个毛病：切页签之后页面弹回顶部。
 */
public class ViewportTest {

    private static int n = 0;
    private static int bad = 0;

    public static void main(String[] args) {
        // ---- clampScroll：正常区间 ----
        eq("滚一半就停一半", 400, Viewport.clampScroll(400, 2000, 1000));
        eq("想滚到 0 就 0", 0, Viewport.clampScroll(0, 2000, 1000));
        eq("想滚过头，钳到最底", 1000, Viewport.clampScroll(999999, 2000, 1000));
        eq("刚好滚到最底", 1000, Viewport.clampScroll(1000, 2000, 1000));

        // ---- 内容不够滚：只能是 0 ----
        eq("内容比窗口矮", 0, Viewport.clampScroll(300, 600, 1000));
        eq("内容恰好等于窗口", 0, Viewport.clampScroll(300, 1000, 1000));

        // ---- 内容还没量出来：不能拿「负高度」算出个负滚动 ----
        eq("窗口高度未知", 0, Viewport.clampScroll(300, 2000, 0));
        eq("窗口高度为负（还没量）", 0, Viewport.clampScroll(300, 2000, -5));
        eq("内容还没量（0 高）", 0, Viewport.clampScroll(300, 0, 1000));

        // ---- 负数输入 ----
        eq("负数当 0", 0, Viewport.clampScroll(-1, 2000, 1000));
        eq("大幅负数", 0, Viewport.clampScroll(Integer.MIN_VALUE, 2000, 1000));

        // ---- 边界：整数溢出不该翻车 ----
        eq("极大 want", 1000000000, Viewport.clampScroll(Integer.MAX_VALUE, 2000000000, 1000000000));

        // ---- 单调性：想滚得更远，落点不该更近 ----
        int prev = -1;
        boolean mono = true;
        for (int want = 0; want <= 2000; want += 50) {
            int got = Viewport.clampScroll(want, 2000, 1000);
            if (got < prev) mono = false;
            prev = got;
        }
        ok("落点随 want 单调不减", mono);

        // ---- previewHeight ----
        // 比例刻意用能精确表示的 1.25，避免断言卡在浮点尾数上
        eq("1.25 倍 + 130", 255, Viewport.previewHeight(100, 1.25f, 130));
        eq("尺寸变大，框跟着高", 305, Viewport.previewHeight(140, 1.25f, 130));
        eq("尺寸变小，框跟着矮", 210, Viewport.previewHeight(64, 1.25f, 130));
        eq("差值正好是增量 × 比例", 50,
                Viewport.previewHeight(140, 1.25f, 130) - Viewport.previewHeight(100, 1.25f, 130));
        ok("框高始终大于角色本身", Viewport.previewHeight(64, 1.25f, 130) > 64);
        eq("余量为 0 时就是角色高度", 125, Viewport.previewHeight(100, 1.25f, 0));
        eq("极端尺寸也不炸", 455, Viewport.previewHeight(260, 1.25f, 130));
        ok("实际用的 1.15 比例下也不小于角色高度",
                Viewport.previewHeight(100, 1.15f, 130) >= 230);
        ok("角色越大框越高", Viewport.previewHeight(140, 1.15f, 130)
                > Viewport.previewHeight(64, 1.15f, 130));

        System.out.println((bad == 0 ? "ViewportTest 通过（" : "ViewportTest 失败（")
                + n + " 项断言" + (bad == 0 ? "）" : "，" + bad + " 处不符）"));
        if (bad != 0) System.exit(1);
    }

    private static void eq(String what, int want, int got) {
        n++;
        if (want != got) {
            bad++;
            System.out.println("  FAIL " + what + "：期望 " + want + "，实际 " + got);
        }
    }

    private static void ok(String what, boolean cond) {
        n++;
        if (!cond) {
            bad++;
            System.out.println("  FAIL " + what);
        }
    }
}

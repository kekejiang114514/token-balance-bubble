package com.coco.balancebubble;

/**
 * 视口算术：滚动位置的钳位、预览框该留多高。
 *
 * <p>这几行单独抽出来，是为了能脱离 Android 在 JVM 上跑单测。设置页那个
 * 「切页签弹回顶部」的毛病就出在这里：重画之后内容还没量过，拿旧高度去钳，
 * 结果永远是 0。
 */
public final class Viewport {

    private Viewport() {
    }

    /**
     * 把想要的滚动位置钳进合法区间。
     *
     * @param want    想滚到哪（像素，≥0）
     * @param content 内容总高（像素）
     * @param window  可视高度（像素）
     * @return 实际能滚到的位置；内容还没量出来（window ≤ 0）或根本不够滚时返回 0
     */
    public static int clampScroll(int want, int content, int window) {
        if (window <= 0 || content <= window) return 0;
        if (want < 0) return 0;
        int max = content - window;
        return want > max ? max : want;
    }

    /** 预览框高度：给角色留出它的高度，再加一块给气泡的余量。 */
    public static int previewHeight(int charSize, float heightRatio, int slack) {
        return (int) (charSize * heightRatio) + slack;
    }
}

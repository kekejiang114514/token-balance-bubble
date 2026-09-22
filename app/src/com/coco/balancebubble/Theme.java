package com.coco.balancebubble;

/**
 * 配色方案：整套界面的颜色都由这里决定，界面代码不再直接写死色值。
 *
 * <p>三件事：
 * <ul>
 *   <li>主题色板（强调色 + 副色，用于按钮、开关、横幅渐变）；</li>
 *   <li>深浅两套中性色（页面底色、卡片、输入框、文字）；</li>
 *   <li>气泡配色 + 靠对比度自动决定气泡上的文字颜色（{@link #inkOn(int)}）。</li>
 * </ul>
 *
 * <p>纯逻辑，不依赖 Android，亮度判据可以在 JVM 上直接单测。
 */
public class Theme {

    /** 外观模式 */
    public static final int LIGHT = 0;
    public static final int DARK = 1;
    public static final int AUTO = 2;
    public static final String[] MODE_NAMES = {"浅色", "深色", "跟随系统"};
    public static final String[] MODE_DESC = {
            "白底浅色界面，白天看着舒服。",
            "深色界面，晚上不刺眼，也更省电（OLED 屏）。",
            "跟着手机系统的深色模式自动切换。",
    };

    public static int clampMode(int v) {
        return v < 0 ? LIGHT : (v > AUTO ? AUTO : v);
    }

    // ---------------- 主题色板 ----------------

    public static final String[] NAMES = {
            "经典蓝", "樱花粉", "薄荷绿", "琥珀橙", "紫罗兰", "石墨灰"
    };
    public static final String[] NOTE = {
            "默认配色，中性耐看。",
            "偏暖，配白底角色立绘很搭。",
            "清爽的绿松石色，适合长时间挂着。",
            "暖橙色，晚上看着不刺眼。",
            "紫调，和蓝色区分度大，一眼能认出。",
            "接近黑白，最低调，几乎不抢桌面注意力。",
    };

    /** 色板用的主题色列表（与 {@link #NAMES} 一一对应）。 */
    public static int[] accentPalette() {
        return ACCENT.clone();
    }

    private static final int[] ACCENT = {
            0xFF2F6FED, 0xFFE0507E, 0xFF0E9C6B, 0xFFE07C10, 0xFF7C4DFF, 0xFF4A5568
    };
    private static final int[] ACCENT_SOFT = {
            0xFFEAF1FF, 0xFFFDEBF1, 0xFFE6F7F0, 0xFFFDF1E0, 0xFFF1EBFF, 0xFFEEF1F6
    };
    private static final int[] ACCENT2 = {
            0xFF6B4BF0, 0xFFFF8FB1, 0xFF37C79A, 0xFFF2B134, 0xFFB388FF, 0xFF8695AC
    };

    public static int count() {
        return NAMES.length;
    }

    public static int clampTheme(int v) {
        return v < 0 ? 0 : (v >= NAMES.length ? NAMES.length - 1 : v);
    }

    public static int accent(int theme) {
        return ACCENT[clampTheme(theme)];
    }

    public static int accent2(int theme) {
        return ACCENT2[clampTheme(theme)];
    }

    public static int accentSoft(int theme) {
        return ACCENT_SOFT[clampTheme(theme)];
    }

    public static String note(int theme) {
        return NOTE[clampTheme(theme)];
    }

    // ---------------- 深浅中性色 ----------------

    public int bg() {
        return dark ? 0xFF12151C : 0xFFEFF3F9;
    }

    public int card() {
        return dark ? 0xFF1B1F28 : 0xFFFFFFFF;
    }

    public int field() {
        return dark ? 0xFF242A35 : 0xFFF4F7FB;
    }

    public int line() {
        return dark ? 0xFF2F3644 : 0xFFE1E8F2;
    }

    public int txt() {
        return dark ? 0xFFF1F4F9 : 0xFF16212D;
    }

    public int sub() {
        return dark ? 0xFF9AA6B8 : 0xFF64748B;
    }

    public int info() {
        return dark ? 0xFF1D2735 : 0xFFEDF3FF;
    }

    public int infoLine() {
        return dark ? 0xFF2C3B52 : 0xFFCBDCFB;
    }

    public int ok() {
        return dark ? 0xFF4ED39A : 0xFF12734F;
    }

    public int okBg() {
        return dark ? 0xFF14301F : 0xFFE7F6EE;
    }

    public int okLine() {
        return dark ? 0xFF25523A : 0xFFB6E2CC;
    }

    public int err() {
        return dark ? 0xFFF2776B : 0xFFC0392B;
    }

    public int errBg() {
        return dark ? 0xFF361C1A : 0xFFFDECEA;
    }

    public int errLine() {
        return dark ? 0xFF5C2C27 : 0xFFF3C9C3;
    }

    /** 关闭状态的开关底色。 */
    public int pillOff() {
        return dark ? 0xFF2A313D : 0xFFF1F4F9;
    }

    /** 预览面板／次级容器底色。 */
    public int panel() {
        return dark ? 0xFF171B23 : 0xFFE7EDF7;
    }

    public final int theme;
    public final boolean dark;

    public Theme(int theme, boolean dark) {
        this.theme = clampTheme(theme);
        this.dark = dark;
    }

    public int accent() {
        return accent(theme);
    }

    public int accent2() {
        return accent2(theme);
    }

    public int accentSoft() {
        return dark ? blend(accent(), 0xFF000000, 0.78f) : accentSoft(theme);
    }

    public int[] heroColors() {
        return new int[]{accent(), accent2()};
    }

    // ---------------- 气泡配色 ----------------

    /** 气泡底色：-1 表示跟随深浅模式。 */
    public int bubbleBg(int custom) {
        if (custom != 0 && custom != -1) return custom;
        return dark ? 0xFF23262F : 0xFFFFFFFF;
    }

    /** 正文墨色（深 / 浅两种候选）。 */
    public static final int INK_DARK = 0xFF16181D;
    public static final int INK_LIGHT = 0xFFF6F8FB;
    private static final int SUB_DARK = 0xFF6B6B76;
    private static final int SUB_LIGHT = 0xFFA9B2C0;
    private static final int ERR_DARK = 0xFFC62828;
    private static final int ERR_LIGHT = 0xFFFF8A80;

    /**
     * 在给定底色上取一个看得清的字色：两个候选里挑对比度更高的那个。
     *
     * <p>早先的写法是按亮度阈值（0.55）二选一，对中间亮度的饱和色会挑错 ——
     * 琥珀橙底色配白字只有 2.79:1，远低于 WCAG 的 3:1 线；改成比对比度后降到深字，6.15:1。
     */
    public static int inkOn(int bg) {
        return contrast(bg, INK_DARK) >= contrast(bg, INK_LIGHT) ? INK_DARK : INK_LIGHT;
    }

    /** 次要文字色：同样比对比度，只是两个候选都比正文墨色浅一档。 */
    public static int subInkOn(int bg) {
        return contrast(bg, SUB_DARK) >= contrast(bg, SUB_LIGHT) ? SUB_DARK : SUB_LIGHT;
    }

    /** 错误色：底色浅时用深红，底色深时用亮红，保证对比度。 */
    public static int errInkOn(int bg) {
        return contrast(bg, ERR_DARK) >= contrast(bg, ERR_LIGHT) ? ERR_DARK : ERR_LIGHT;
    }

    /** WCAG 对比度（1 到 21），与单测里用的是同一个公式。 */
    public static float contrast(int a, int b) {
        float la = luminance(a) + 0.05f;
        float lb = luminance(b) + 0.05f;
        float hi = Math.max(la, lb);
        float lo = Math.min(la, lb);
        return hi / lo;
    }

    /** sRGB 相对亮度，0（黑）~ 1（白）。 */
    public static float luminance(int color) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        return 0.2126f * lin(r) + 0.7152f * lin(g) + 0.0722f * lin(b);
    }

    private static float lin(float c) {
        return c <= 0.04045f ? c / 12.92f : (float) Math.pow((c + 0.055f) / 1.055f, 2.4f);
    }

    /** 把 a 按比例 t 混向 b，t=0 取 a，t=1 取 b。 */
    public static int blend(int a, int b, float t) {
        float k = t < 0 ? 0 : (t > 1 ? 1 : t);
        int r = (int) (((a >> 16) & 0xFF) * (1 - k) + ((b >> 16) & 0xFF) * k);
        int g = (int) (((a >> 8) & 0xFF) * (1 - k) + ((b >> 8) & 0xFF) * k);
        int bl = (int) ((a & 0xFF) * (1 - k) + (b & 0xFF) * k);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    /** 气泡调色板：自定义底色时可选的颜色。 */
    public static final String[] SWATCH_NAMES = {
            "自动", "纯白", "米白", "浅蓝", "浅绿", "浅粉", "深灰", "墨黑", "奶油", "淡紫"
    };
    public static final int[] SWATCH = {
            0, 0xFFFFFFFF, 0xFFFBF6EC, 0xFFE8F1FF, 0xFFE9F7F0, 0xFFFDEBF1,
            0xFF3A4050, 0xFF1C1F26, 0xFFFFF3D6, 0xFFF0EBFF
    };
}

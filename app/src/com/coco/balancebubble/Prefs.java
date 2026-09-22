package com.coco.balancebubble;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

/**
 * 全部设置项。
 *
 * <p>分四组：接口（服务商与密钥）、外观（主题与深浅）、气泡（尺寸/配色/行为）、
 * 角色（模式/大小/活泼度/互动）。每一项都有一个默认值和一个取值范围，
 * 取值范围集中在 clamp* 里，界面与导入都走同一套钳制，避免出现「设置里能填进去、
 * 渲染时算不出来」的脏数据。
 */
public class Prefs {
    private static final String F = "bubble";

    public static SharedPreferences get(Context c) {
        return c.getSharedPreferences(F, Context.MODE_PRIVATE);
    }

    public static String s(Context c, String k, String d) {
        String v = get(c).getString(k, d);
        return (v == null) ? d : v;
    }

    public static void set(Context c, String k, String v) {
        get(c).edit().putString(k, v).apply();
    }

    public static int i(Context c, String k, int d) {
        return get(c).getInt(k, d);
    }

    public static boolean b(Context c, String k, boolean d) {
        return get(c).getBoolean(k, d);
    }

    public static float f(Context c, String k, float d) {
        return get(c).getFloat(k, d);
    }

    // ---------------- 设置集合 ----------------

    /** 界面上一次能改的全部参数。 */
    public static class Draft {
        // ---- 接口 ----
        public String provider = "DeepSeek";
        public String label = "DeepSeek";
        public String base = "https://api.deepseek.com";
        public String path = "/user/balance";
        public String key = "";
        public String extract = "balance_infos.0.total_balance";
        public String header = "Authorization";
        public String prefix = "Bearer ";
        public String curCode = "CNY";
        public String curCustom = "";
        public int interval = 5;

        // ---- 模式 ----
        /** 三种模式：MODE_TOKEN / MODE_PET / MODE_MIXED */
        public int mode = MODE_MIXED;

        // ---- 外观 ----
        public int theme = 0;
        public int dark = Theme.LIGHT;
        /** 系统当前是不是深色（dark=AUTO 时用它判断）。 */
        public boolean autoDark = false;

        // ---- 气泡 ----
        public int bubbleMaxW = 300;
        public int bubbleMinW = 72;
        public int bubbleFont = 22;
        public int bubbleMinFont = 14;
        public int bubbleLines = 3;
        public int bubbleHardLines = 8;
        public int bubbleRadius = 18;
        public int bubblePadH = 15;
        public int bubblePadT = 9;
        public int bubblePadB = 11;
        public int bubbleTail = 9;
        public float bubbleAlpha = 1f;
        /** 0 表示跟随深浅模式自动选底色。 */
        public int bubbleBg = 0;
        public boolean bubbleShadow = true;
        public boolean bubbleTailUp = false;
        public boolean bubbleAnim = true;
        public int bubbleAnimMs = 180;
        public int bubbleHideMs = 7000;
        public boolean showTitle = true;
        public boolean showCode = false;

        // ---- 角色 ----
        public int charSize = 96;
        public boolean showChar = true;
        public boolean animate = true;
        /** 0 安静 / 1 适中 / 2 活泼 */
        public int petIdle = 1;
        /** 自动说话间隔（秒），0 表示不自动说话。 */
        public int talkSec = 60;

        // ---- 互动 ----
        /** 0 按模式 / 1 只说一句 / 2 只刷新余额 / 3 开关气泡 */
        public int tapAction = 0;
        /** 0 打开设置 / 1 收起气泡 / 2 什么都不做 */
        public int longTapAction = 0;
        /** 余额变化时主动冒泡提醒。 */
        public boolean notice = true;

        public boolean configured() {
            return base != null && !base.trim().isEmpty() && key != null && !key.trim().isEmpty();
        }

        /** 当前是否按深色渲染。 */
        public boolean darkNow() {
            return dark == Theme.DARK || (dark == Theme.AUTO && autoDark);
        }

        public Theme theme() {
            return new Theme(theme, darkNow());
        }

        /** 存盘。界面每改一项就调一次，不需要「保存」按钮。 */
        public void save(Context c) {
            Prefs.save(c, this);
        }
    }

    public static Draft load(Context c) {
        Draft d = new Draft();
        d.provider = provider(c);
        d.label = label(c);
        d.base = base(c);
        d.path = path(c);
        d.key = key(c);
        d.extract = extract(c);
        d.header = authHeader(c);
        d.prefix = authPrefix(c);
        d.curCode = curCode(c);
        d.curCustom = curCustom(c);
        d.interval = interval(c);
        d.mode = mode(c);
        d.theme = theme(c);
        d.dark = darkMode(c);
        d.autoDark = systemDark(c);
        d.bubbleMaxW = bubbleMaxW(c);
        d.bubbleMinW = bubbleMinW(c);
        d.bubbleFont = bubbleFont(c);
        d.bubbleMinFont = bubbleMinFont(c);
        d.bubbleLines = bubbleLines(c);
        d.bubbleHardLines = bubbleHardLines(c);
        d.bubbleRadius = bubbleRadius(c);
        d.bubblePadH = bubblePadH(c);
        d.bubblePadT = bubblePadT(c);
        d.bubblePadB = bubblePadB(c);
        d.bubbleTail = bubbleTail(c);
        d.bubbleAlpha = bubbleAlpha(c);
        d.bubbleBg = bubbleBg(c);
        d.bubbleShadow = bubbleShadow(c);
        d.bubbleTailUp = bubbleTailUp(c);
        d.bubbleAnim = bubbleAnim(c);
        d.bubbleAnimMs = bubbleAnimMs(c);
        d.bubbleHideMs = bubbleHideMs(c);
        d.showTitle = showTitle(c);
        d.showCode = showCode(c);
        d.charSize = charSize(c);
        d.showChar = showChar(c);
        d.animate = animate(c);
        d.petIdle = petIdle(c);
        d.talkSec = talkSec(c);
        d.tapAction = tapAction(c);
        d.longTapAction = longTapAction(c);
        d.notice = notice(c);
        return d;
    }

    public static void save(Context c, Draft d) {
        get(c).edit()
                .putString("provider", d.provider)
                .putString("label", d.label)
                .putString("base", d.base)
                .putString("path", d.path)
                .putString("key", d.key)
                .putString("extract", d.extract)
                .putString("authheader", d.header)
                .putString("authprefix", d.prefix)
                .putString("curcode", d.curCode)
                .putString("curcustom", d.curCustom)
                .putInt("interval", clampInterval(d.interval))
                .putInt("mode", clampMode(d.mode))
                .putInt("theme", Theme.clampTheme(d.theme))
                .putInt("dark", Theme.clampMode(d.dark))
                .putInt("bmaxw", clampBubbleMaxW(d.bubbleMaxW))
                .putInt("bminw", clampBubbleMinW(d.bubbleMinW))
                .putInt("bfont", clampBubbleFont(d.bubbleFont))
                .putInt("bminfont", clampBubbleMinFont(d.bubbleMinFont))
                .putInt("blines", clampBubbleLines(d.bubbleLines))
                .putInt("bhardlines", clampBubbleLines(d.bubbleHardLines))
                .putInt("bradius", clampRadius(d.bubbleRadius))
                .putInt("bpadh", clampPad(d.bubblePadH))
                .putInt("bpadt", clampPad(d.bubblePadT))
                .putInt("bpadb", clampPad(d.bubblePadB))
                .putInt("btail", clampTail(d.bubbleTail))
                .putFloat("balpha", clampAlpha(d.bubbleAlpha))
                .putInt("bbg", d.bubbleBg)
                .putBoolean("bshadow", d.bubbleShadow)
                .putBoolean("btailup", d.bubbleTailUp)
                .putBoolean("banim", d.bubbleAnim)
                .putInt("banimms", clampAnimMs(d.bubbleAnimMs))
                .putInt("bhide", clampHide(d.bubbleHideMs))
                .putBoolean("showtitle", d.showTitle)
                .putBoolean("showcode", d.showCode)
                .putInt("charsize", clampCharSize(d.charSize))
                .putBoolean("showchar", d.showChar)
                .putBoolean("animate", d.animate)
                .putInt("petidle", clampPetIdle(d.petIdle))
                .putInt("talksec", clampTalkSec(d.talkSec))
                .putInt("tap", clampTap(d.tapAction))
                .putInt("longtap", clampLongTap(d.longTapAction))
                .putBoolean("notice", d.notice)
                .apply();
    }

    // ---------------- 取值范围 ----------------

    public static int clampInterval(int v) {
        return v < 1 ? 1 : (v > 720 ? 720 : v);
    }

    public static int clampCharSize(int v) {
        return v < 40 ? 40 : (v > 260 ? 260 : v);
    }

    public static int clampBubbleMaxW(int v) {
        return v < 140 ? 140 : (v > 340 ? 340 : v);
    }

    public static int clampBubbleMinW(int v) {
        return v < 40 ? 40 : (v > 240 ? 240 : v);
    }

    public static int clampBubbleFont(int v) {
        return v < 13 ? 13 : (v > 34 ? 34 : v);
    }

    public static int clampBubbleMinFont(int v) {
        return v < 9 ? 9 : (v > 24 ? 24 : v);
    }

    public static int clampBubbleLines(int v) {
        return v < 1 ? 1 : (v > 8 ? 8 : v);
    }

    public static int clampRadius(int v) {
        return v < 2 ? 2 : (v > 28 ? 28 : v);
    }

    public static int clampPad(int v) {
        return v < 4 ? 4 : (v > 26 ? 26 : v);
    }

    public static int clampTail(int v) {
        return v < 0 ? 0 : (v > 18 ? 18 : v);
    }

    public static float clampAlpha(float v) {
        return v < 0.35f ? 0.35f : (v > 1f ? 1f : v);
    }

    public static int clampAnimMs(int v) {
        return v < 0 ? 0 : (v > 600 ? 600 : v);
    }

    public static int clampHide(int v) {
        return v < 1500 ? 1500 : (v > 60000 ? 60000 : v);
    }

    public static int clampPetIdle(int v) {
        return v < 0 ? 0 : (v > 2 ? 2 : v);
    }

    public static int clampTalkSec(int v) {
        return v < 0 ? 0 : (v > 1800 ? 1800 : v);
    }

    public static int clampTap(int v) {
        return v < 0 ? 0 : (v > 3 ? 3 : v);
    }

    public static int clampLongTap(int v) {
        return v < 0 ? 0 : (v > 2 ? 2 : v);
    }

    // ---------------- 提供商 ----------------

    public static String provider(Context c) { return s(c, "provider", "DeepSeek"); }
    public static String label(Context c) { return s(c, "label", "DeepSeek"); }
    public static String base(Context c) { return s(c, "base", "https://api.deepseek.com"); }
    public static String path(Context c) { return s(c, "path", "/user/balance"); }
    public static String key(Context c) { return s(c, "key", ""); }
    public static String extract(Context c) { return s(c, "extract", "balance_infos.0.total_balance"); }
    public static String authHeader(Context c) { return s(c, "authheader", "Authorization"); }
    public static String authPrefix(Context c) { return s(c, "authprefix", "Bearer "); }

    // ---------------- 币种 ----------------

    public static String curCode(Context c) { return s(c, "curcode", Currencies.code(Currencies.DEFAULT_INDEX)); }
    public static String curCustom(Context c) { return s(c, "curcustom", ""); }
    public static boolean showCode(Context c) { return b(c, "showcode", false); }

    /** 气泡上显示的币种前缀。接口返回的币种只在「自动识别」时用到。 */
    public static String currency(Context c) {
        return Currencies.prefix(curCode(c), curCustom(c), "", false);
    }

    public static String suffix(Context c) {
        return Currencies.suffix(curCode(c), showCode(c));
    }

    // ---------------- 显示 ----------------

    public static int interval(Context c) { return clampInterval(i(c, "interval", 5)); }

    public static int theme(Context c) { return Theme.clampTheme(i(c, "theme", 0)); }
    public static int darkMode(Context c) { return Theme.clampMode(i(c, "dark", Theme.LIGHT)); }

    /** 系统当前是不是深色模式。 */
    public static boolean systemDark(Context c) {
        int ui = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return ui == Configuration.UI_MODE_NIGHT_YES;
    }

    /** 最终是否按深色渲染（AUTO 时看系统）。 */
    public static boolean dark(Context c) {
        int m = darkMode(c);
        return m == Theme.DARK || (m == Theme.AUTO && systemDark(c));
    }

    public static Theme themeFor(Context c) {
        return new Theme(theme(c), dark(c));
    }

    public static int bubbleMaxW(Context c) { return clampBubbleMaxW(i(c, "bmaxw", 300)); }
    public static int bubbleMinW(Context c) { return clampBubbleMinW(i(c, "bminw", 72)); }
    public static int bubbleFont(Context c) { return clampBubbleFont(i(c, "bfont", 22)); }
    public static int bubbleMinFont(Context c) { return clampBubbleMinFont(i(c, "bminfont", 14)); }
    public static int bubbleLines(Context c) { return clampBubbleLines(i(c, "blines", 3)); }
    public static int bubbleHardLines(Context c) { return clampBubbleLines(i(c, "bhardlines", 8)); }
    public static int bubbleRadius(Context c) { return clampRadius(i(c, "bradius", 18)); }
    public static int bubblePadH(Context c) { return clampPad(i(c, "bpadh", 15)); }
    public static int bubblePadT(Context c) { return clampPad(i(c, "bpadt", 9)); }
    public static int bubblePadB(Context c) { return clampPad(i(c, "bpadb", 11)); }
    public static int bubbleTail(Context c) { return clampTail(i(c, "btail", 9)); }
    public static float bubbleAlpha(Context c) { return clampAlpha(f(c, "balpha", 1f)); }
    public static int bubbleBg(Context c) { return i(c, "bbg", 0); }
    public static boolean bubbleShadow(Context c) { return b(c, "bshadow", true); }
    public static boolean bubbleTailUp(Context c) { return b(c, "btailup", false); }
    public static boolean bubbleAnim(Context c) { return b(c, "banim", true); }
    public static int bubbleAnimMs(Context c) { return clampAnimMs(i(c, "banimms", 180)); }
    public static int bubbleHideMs(Context c) { return clampHide(i(c, "bhide", 7000)); }
    public static boolean showTitle(Context c) { return b(c, "showtitle", true); }

    // ---------------- 模式 ----------------

    /** 只查余额：定时静默刷新，点角色弹气泡。 */
    public static final int MODE_TOKEN = 0;
    /** 只当桌宠：不查余额，定时说话做动作。 */
    public static final int MODE_PET = 1;
    /** 混合：定时静默查余额＋定时说话做动作，余额变了会主动冒泡。 */
    public static final int MODE_MIXED = 2;

    public static final String[] MODE_NAMES = {
            "仅 token 查询", "仅桌宠", "混合模式（推荐）"};

    public static final String[] MODE_DESC = {
            "定时静默刷新余额，点角色立刻查询并把气泡弹出来。",
            "完全不查余额，点角色随机说句卖萌话并做动作，按下面的节奏自动说话。",
            "后台定时静默查余额，同时保留桌宠的说话与动作；点角色＝查余额，"
                    + "余额有变化时它会主动冒个泡提醒你。"};

    public static int clampMode(int v) {
        return (v == MODE_TOKEN || v == MODE_PET || v == MODE_MIXED) ? v : MODE_MIXED;
    }

    /** 这个模式是干什么的，设置页选完之后在下面解释一句。 */
    public static String modeNote(int v) {
        return MODE_DESC[clampMode(v)];
    }

    /**
     * 当前模式。
     *
     * <p>老版本只有「token 查询模式」开关，这里做一次兼容：开＝仅查询，关＝仅桌宠。
     */
    public static int mode(Context c) {
        if (get(c).contains("mode")) return clampMode(i(c, "mode", MODE_MIXED));
        if (get(c).contains("tokenenabled")) {
            return b(c, "tokenenabled", true) ? MODE_TOKEN : MODE_PET;
        }
        return MODE_MIXED;
    }

    public static void setMode(Context c, int v) {
        get(c).edit().putInt("mode", clampMode(v)).apply();
    }

    /** 这个模式下要不要查余额。 */
    public static boolean queriesBalance(Context c) {
        int m = mode(c);
        return m == MODE_TOKEN || m == MODE_MIXED;
    }

    /** 这个模式下要不要定时说话做动作。 */
    public static boolean speaks(Context c) {
        int m = mode(c);
        return m == MODE_PET || m == MODE_MIXED;
    }

    // ---------------- 角色 ----------------

    /** 角色是否播放骨架动作。 */
    public static boolean animate(Context c) { return b(c, "animate", true); }

    public static void setAnimate(Context c, boolean v) {
        get(c).edit().putBoolean("animate", v).apply();
    }

    public static boolean showChar(Context c) { return b(c, "showchar", true); }

    public static void setShowChar(Context c, boolean v) {
        get(c).edit().putBoolean("showchar", v).apply();
    }

    public static int charSize(Context c) { return clampCharSize(i(c, "charsize", 96)); }

    public static int petIdle(Context c) { return clampPetIdle(i(c, "petidle", 1)); }

    public static final String[] IDLE_NAMES = {"安静", "适中", "活泼"};
    public static final String[] IDLE_DESC = {
            "很少自己做小动作，适合专心干活时挂在角上。",
            "每 3~7 秒随机来一个小动作。",
            "动作连着来，眨眼和发丝也更勤快，最热闹。",
    };

    /** 自动说话间隔（秒），0 表示不自动说话。 */
    public static int talkSec(Context c) { return clampTalkSec(i(c, "talksec", 60)); }

    public static final int[] TALK_OPTIONS = {0, 30, 60, 180, 600};
    public static final String[] TALK_NAMES = {
            "不自动说话", "每 30 秒", "每 1 分钟", "每 3 分钟", "每 10 分钟"};

    // ---------------- 互动 ----------------

    public static final int TAP_MODE = 0;
    public static final int TAP_TALK = 1;
    public static final int TAP_REFRESH = 2;
    public static final int TAP_BUBBLE = 3;

    public static final String[] TAP_NAMES = {
            "按模式来", "只说一句话", "只刷新余额", "开关气泡"};
    public static final String[] TAP_DESC = {
            "跟随上面的模式：查余额的模式下点一下＝刷新，桌宠模式下点一下＝说句话。",
            "点一下就随机说句卖萌话并做个动作，不发起网络请求。",
            "点一下立刻查询余额并把结果弹出来，不说话。",
            "点一下把气泡弹出来或者收起来，适合只想偶尔看一眼。",
    };

    public static int tapAction(Context c) { return clampTap(i(c, "tap", 0)); }

    public static final int LONG_SETTINGS = 0;
    public static final int LONG_HIDE = 1;
    public static final int LONG_NONE = 2;

    public static final String[] LONG_NAMES = {"打开设置", "收起气泡", "什么都不做"};
    public static final String[] LONG_DESC = {
            "长按角色打开这个设置页。",
            "长按把气泡收起来（下次点角色还会出来）。",
            "长按不触发任何操作，避免误触。",
    };

    public static int longTapAction(Context c) { return clampLongTap(i(c, "longtap", 0)); }

    /** 余额变化时主动冒泡提醒。 */
    public static boolean notice(Context c) { return b(c, "notice", true); }

    /** 待机动作间隔的倍率：安静 1.8×、适中 1×、活泼 0.55×。 */
    public static float idleScale(Context c) {
        int v = petIdle(c);
        return v == 0 ? 1.8f : (v == 2 ? 0.55f : 1f);
    }

    // ---------------- 运行状态 ----------------

    public static boolean running(Context c) { return b(c, "running", false); }
    public static void setRunning(Context c, boolean v) {
        get(c).edit().putBoolean("running", v).apply();
    }
    public static String lastAmount(Context c) { return s(c, "last", "--"); }
    public static String lastInfo(Context c) { return s(c, "lastinfo", ""); }
    public static boolean lastError(Context c) { return b(c, "lasterr", false); }
    public static void setLast(Context c, String amount) {
        get(c).edit().putString("last", amount).apply();
    }
    public static void setLastErr(Context c, String msg) {
        boolean bad = (msg != null && !msg.isEmpty());
        get(c).edit().putString("lastinfo", bad ? msg : "").putBoolean("lasterr", bad).apply();
    }

    // ---------------- 气泡位置 ----------------

    public static float posX(Context c) { return get(c).getFloat("px", -1f); }
    public static float posY(Context c) { return get(c).getFloat("py", -1f); }
    public static void setPos(Context c, float x, float y) {
        get(c).edit().putFloat("px", x).putFloat("py", y).apply();
    }
    /** 忘记拖过的位置，回到默认位置。 */
    public static void clearPos(Context c) {
        get(c).edit().remove("px").remove("py").apply();
    }
}

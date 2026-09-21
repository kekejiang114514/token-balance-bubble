package com.coco.balancebubble;

import android.content.Context;
import android.content.SharedPreferences;

/** 全部设置项，支持自定义 API 提供商。 */
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

    // ---------------- 设置集合 ----------------

    /** 界面上一次能改的全部参数。 */
    public static class Draft {
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
        public int charSize = 96;
        public boolean showChar = true;
        public boolean showCode = false;
        /** 三种模式：MODE_TOKEN / MODE_PET / MODE_MIXED */
        public int mode = MODE_MIXED;
        /** 角色是否播放骨架动作 */
        public boolean animate = true;

        public boolean configured() {
            return base != null && !base.trim().isEmpty() && key != null && !key.trim().isEmpty();
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
        d.charSize = charSize(c);
        d.showChar = showChar(c);
        d.showCode = showCode(c);
        d.mode = mode(c);
        d.animate = animate(c);
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
                .putInt("charsize", clampCharSize(d.charSize))
                .putBoolean("showchar", d.showChar)
                .putBoolean("showcode", d.showCode)
                .putInt("mode", clampMode(d.mode))
                .putBoolean("animate", d.animate)
                .apply();
    }

    public static int clampInterval(int v) {
        return v < 1 ? 1 : (v > 720 ? 720 : v);
    }

    public static int clampCharSize(int v) {
        return v < 40 ? 40 : (v > 260 ? 260 : v);
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
            "完全不查余额，点角色随机说句卖萌话并做动作，每分钟自动来一次。",
            "后台定时静默查余额，同时保留桌宠的说话与动作；点角色＝查余额，"
                    + "余额有变化时它会主动冒个泡提醒你。"};

    public static int clampMode(int v) {
        return (v == MODE_TOKEN || v == MODE_PET || v == MODE_MIXED) ? v : MODE_MIXED;
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

package com.coco.balancebubble;

import java.util.Locale;

/** 币种表：代码 / 符号 / 中文名。默认人民币（CNY）。 */
public class Currencies {

    /** 跟随接口返回的币种 */
    public static final String AUTO = "AUTO";
    /** 用户手填符号 */
    public static final String CUSTOM = "CUSTOM";

    public static final String[] CODES = {
            "CNY", "USD", "EUR", "GBP", "JPY", "HKD", "TWD", "KRW", "SGD",
            "AUD", "CAD", "INR", "RUB", "THB", "MYR", AUTO, CUSTOM
    };
    public static final String[] SYMBOLS = {
            "\u00a5", "$", "\u20ac", "\u00a3", "JP\u00a5", "HK$", "NT$", "\u20a9", "S$",
            "A$", "C$", "\u20b9", "\u20bd", "\u0e3f", "RM", "", ""
    };
    public static final String[] NAMES = {
            "人民币", "美元", "欧元", "英镑", "日元", "港币", "新台币", "韩元", "新加坡元",
            "澳元", "加元", "印度卢比", "卢布", "泰铢", "马来西亚林吉特", "", ""
    };

    /** 默认选中项：人民币 CNY */
    public static final int DEFAULT_INDEX = 0;

    public static int clamp(int i) {
        return (i < 0 || i >= CODES.length) ? DEFAULT_INDEX : i;
    }

    /** 下拉框里显示的文字，尽量让不懂代码的人也能选对。 */
    public static String label(int i) {
        i = clamp(i);
        if (AUTO.equals(CODES[i])) return "自动识别（用接口返回的币种）";
        if (CUSTOM.equals(CODES[i])) return "自定义符号（在高级设置里填）";
        return NAMES[i] + " " + CODES[i] + " " + SYMBOLS[i];
    }

    public static String code(int i) {
        return CODES[clamp(i)];
    }

    public static String symbol(int i) {
        return SYMBOLS[clamp(i)];
    }

    public static boolean isAuto(int i) {
        return AUTO.equals(CODES[clamp(i)]);
    }

    public static boolean isCustom(int i) {
        return CUSTOM.equals(CODES[clamp(i)]);
    }

    public static int indexOfCode(String c) {
        if (c == null) return DEFAULT_INDEX;
        String v = c.trim();
        if (v.isEmpty()) return DEFAULT_INDEX;
        for (int i = 0; i < CODES.length; i++) {
            if (CODES[i].equalsIgnoreCase(v)) return i;
        }
        return indexOf(CUSTOM);
    }

    public static int indexOf(String code) {
        for (int i = 0; i < CODES.length; i++) {
            if (CODES[i].equals(code)) return i;
        }
        return DEFAULT_INDEX;
    }

    /** 把接口返回的币种代码（如 "CNY" / "USD"）换成符号。 */
    public static String symbolOfCode(String code) {
        if (code == null) return "";
        String c = code.trim().toUpperCase(Locale.US);
        if (c.isEmpty()) return "";
        if (c.equals("RMB") || c.equals("CNH")) c = "CNY";
        if (c.equals("USDT") || c.equals("USDC")) c = "USD";
        for (int i = 0; i < CODES.length; i++) {
            if (CODES[i].equals(c)) return SYMBOLS[i];
        }
        return code.trim();
    }

    /**
     * 计算气泡上最终显示的币种前缀。
     *
     * @param code      用户选的币种代码（CNY / AUTO / CUSTOM ...）
     * @param customSym 自定义符号
     * @param fromApi   接口返回的原始币种代码（可能为空）
     * @param showCode  是否在金额后面再补一个代码，如 "33.83 CNY"
     */
    public static String prefix(String code, String customSym, String fromApi, boolean showCode) {
        String c = code == null ? "" : code.trim().toUpperCase(Locale.US);
        if (AUTO.equals(c)) {
            String s = symbolOfCode(fromApi);
            return s == null ? "" : s;
        }
        if (CUSTOM.equals(c)) {
            return customSym == null ? "" : customSym;
        }
        if (c.isEmpty()) return symbolOfCode(fromApi);
        return symbolOfCode(c);
    }

    /** 金额后面是否要补币种代码。 */
    public static String suffix(String code, boolean showCode) {
        if (!showCode) return "";
        String c = code == null ? "" : code.trim().toUpperCase(Locale.US);
        if (c.isEmpty() || AUTO.equals(c) || CUSTOM.equals(c)) return "";
        return " " + c;
    }
}

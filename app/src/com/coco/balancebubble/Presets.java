package com.coco.balancebubble;

/** 各提供商的余额接口预设。所有端点均已实测存在（未授权返回 401 而非 404）。 */
public class Presets {

    public static final String[] NAMES = {
            "DeepSeek", "硅基流动", "Moonshot", "OpenRouter", "OpenAI", "自定义"
    };
    /** 气泡上默认显示的名称 */
    public static final String[] LABEL = {
            "DeepSeek", "硅基流动", "Moonshot", "OpenRouter", "OpenAI", "我的服务"
    };
    public static final String[] BASE = {
            "https://api.deepseek.com",
            "https://api.siliconflow.cn",
            "https://api.moonshot.cn",
            "https://openrouter.ai",
            "https://api.openai.com",
            ""
    };
    public static final String[] PATH = {
            "/user/balance",
            "/v1/user/info",
            "/v1/users/me/balance",
            "/api/v1/credits",
            "/v1/dashboard/billing/credit_grants",
            ""
    };
    public static final String[] EXTRACT = {
            "balance_infos.0.total_balance",
            "data.balance",
            "data.available_balance",
            "data.total_credits",
            "total_available",
            ""
    };
    /** 各服务商计价币种（币种下拉框的默认选中项） */
    public static final String[] CURRENCY = {"CNY", "CNY", "CNY", "USD", "USD", "CNY"};

    /** 少数需要额外请求头的服务商（如 OpenRouter 的 Referer）。留空表示不需要。 */
    public static final String[] NOTE = {
            "DeepSeek 官方接口，余额单位为人民币",
            "硅基流动（SiliconFlow），返回平台余额",
            "Moonshot 开放平台账户余额",
            "OpenRouter 显示剩余额度，单位美元",
            "OpenAI 平台额度（需较老的 Key 才能查）",
            "自己填接口地址"
    };

    public static final int CUSTOM_INDEX = NAMES.length - 1;

    public static int indexOfName(String n) {
        if (n != null) {
            for (int i = 0; i < NAMES.length; i++) {
                if (NAMES[i].equals(n)) return i;
            }
        }
        // 不认识的名字（空值、旧版本残留）归到「自定义」，避免误当成 DeepSeek 覆盖掉用户填的地址
        return CUSTOM_INDEX;
    }

    public static String label(int i) {
        i = (i < 0 || i >= NAMES.length) ? 0 : i;
        return LABEL[i];
    }

    public static String base(int i) {
        i = (i < 0 || i >= NAMES.length) ? 0 : i;
        return BASE[i];
    }

    public static String path(int i) {
        i = (i < 0 || i >= NAMES.length) ? 0 : i;
        return PATH[i];
    }

    public static String extract(int i) {
        i = (i < 0 || i >= NAMES.length) ? 0 : i;
        return EXTRACT[i];
    }

    public static String currency(int i) {
        i = (i < 0 || i >= NAMES.length) ? 0 : i;
        return CURRENCY[i];
    }

    public static String note(int i) {
        i = (i < 0 || i >= NAMES.length) ? 0 : i;
        return NOTE[i];
    }

    public static boolean isCustom(int i) {
        return i == CUSTOM_INDEX;
    }
}

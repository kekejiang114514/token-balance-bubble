package com.coco.balancebubble;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;

/** 通用余额查询：任意提供商 / 任意路径 / 任意 JSON 取值路径。 */
public class BalanceApi {

    /** 自动识别时优先匹配的字段名（按优先级）。 */
    private static final String[] AUTO_KEYS = {
            "total_balance", "available_balance", "balance", "total_available",
            "total_credits", "credits", "remaining_balance", "remaining",
            "remain", "available", "amount", "money", "quota", "left",
            "balance_amount", "total", "value", "data", "result"
    };

    public static class Result {
        public boolean ok;
        public String amount = "--";
        public String currency = "";
        public String label = "";
        public boolean available = true;
        public String raw = "";
        public String error = "";
        public String usedPath = "";
    }

    public static Result query(Context c) {
        return query(c, Prefs.base(c), Prefs.path(c), Prefs.key(c),
                Prefs.authHeader(c), Prefs.authPrefix(c), Prefs.extract(c),
                Prefs.curCode(c), Prefs.curCustom(c), Prefs.showCode(c), Prefs.label(c));
    }

    public static Result query(Context c, String base, String path, String key,
                               String authHeader, String authPrefix, String extract,
                               String curCode, String curCustom, boolean showCode,
                               String label) {
        Result r = new Result();
        r.label = label;
        if (key == null || key.trim().isEmpty()) {
            r.error = "还没填 API Key。去服务商官网的「API Keys」页面复制一个，粘贴到上面。";
            return r;
        }
        String url = join(base, path);
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "TokenBalance/1.2");
            String hn = (authHeader == null || authHeader.trim().isEmpty())
                    ? "Authorization" : authHeader.trim();
            String hp = authPrefix == null ? "Bearer " : authPrefix;
            conn.setRequestProperty(hn, hp + key.trim());

            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream()
                    : conn.getErrorStream();
            String body = read(is);
            r.raw = body;

            if (code < 200 || code >= 300) {
                r.error = httpError(code, body);
                return r;
            }
            Object root = new JSONObject(body);
            parse(root, extract, r, curCode, curCustom, showCode);
            return r;
        } catch (Exception e) {
            r.error = netError(e);
            if (r.raw == null) r.raw = "";
            return r;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** 从响应里取值：优先用用户指定路径，否则自动识别。 */
    private static void parse(Object root, String extract, Result r,
                              String curCode, String curCustom, boolean showCode) {
        Object v = null;
        Object parent = null;
        if (extract != null && !extract.trim().isEmpty()) {
            v = dig(root, extract.trim());
            parent = digParent(root, extract.trim());
            r.usedPath = extract.trim();
            if (v == null) {
                r.error = "按路径 " + extract.trim() + " 没取到值。"
                        + "可以在高级设置里换成别的字段，或者留空让它自动识别。";
                return;
            }
        } else {
            v = autoFind(root);
            r.usedPath = "自动识别";
            if (v == null) {
                r.error = "没能自动认出余额字段。请到「高级设置 → 金额字段路径」里手填，"
                        + "例如 balance_infos.0.total_balance";
                return;
            }
        }
        Double d = num(v);
        if (d == null) {
            r.error = "取到的值不是数字：" + String.valueOf(v);
            return;
        }
        // 接口自己报的币种，只在「自动识别」模式下采用
        String apiCode = "";
        if (parent instanceof JSONObject) {
            apiCode = ((JSONObject) parent).optString("currency", "");
        }
        String sym = Currencies.prefix(curCode, curCustom, apiCode, showCode);
        r.currency = sym == null ? "" : sym;
        r.amount = fmt(d) + Currencies.suffix(curCode, showCode);
        Boolean av = findAvailable(root);
        if (av != null) r.available = av;
        if (!r.available) r.error = "账户当前不可用（余额可能已耗尽）";
        r.ok = true;
    }

    /** 点路径取值，支持数组下标：balance_infos.0.total_balance */
    public static Object dig(Object node, String path) {
        String[] parts = path.split("\\.");
        Object cur = node;
        for (String p : parts) {
            if (cur == null) return null;
            p = p.trim();
            if (p.isEmpty()) continue;
            if (cur instanceof JSONArray) {
                int idx;
                try {
                    idx = Integer.parseInt(p);
                } catch (Exception e) {
                    return null;
                }
                cur = ((JSONArray) cur).opt(idx);
            } else if (cur instanceof JSONObject) {
                JSONObject o = (JSONObject) cur;
                if (!o.has(p)) return null;
                cur = o.opt(p);
            } else {
                return null;
            }
        }
        return cur;
    }

    private static Object digParent(Object node, String path) {
        int i = path.lastIndexOf('.');
        if (i <= 0) return node;
        return dig(node, path.substring(0, i));
    }

    /** 广度优先找第一个像余额的数值字段。 */
    private static Object autoFind(Object root) {
        LinkedList<Object> q = new LinkedList<Object>();
        q.add(root);
        int guard = 0;
        while (!q.isEmpty() && guard++ < 3000) {
            Object n = q.poll();
            if (n instanceof JSONObject) {
                JSONObject o = (JSONObject) n;
                for (int i = 0; i < AUTO_KEYS.length; i++) {
                    String k = AUTO_KEYS[i];
                    if (!o.has(k)) continue;
                    Object v = o.opt(k);
                    if (num(v) != null) return v;
                }
                Iterator<String> it = o.keys();
                while (it.hasNext()) {
                    Object v = o.opt(it.next());
                    if (v instanceof JSONObject || v instanceof JSONArray) q.add(v);
                }
            } else if (n instanceof JSONArray) {
                JSONArray a = (JSONArray) n;
                for (int i = 0; i < a.length(); i++) {
                    Object v = a.opt(i);
                    if (v instanceof JSONObject || v instanceof JSONArray) q.add(v);
                }
            }
        }
        return null;
    }

    private static boolean hasKey(Object node, String key) {
        return node instanceof JSONObject && ((JSONObject) node).has(key);
    }

    /** 找 is_available / available 之类的布尔状态。 */
    private static Boolean findAvailable(Object root) {
        LinkedList<Object> q = new LinkedList<Object>();
        q.add(root);
        int guard = 0;
        while (!q.isEmpty() && guard++ < 3000) {
            Object n = q.poll();
            if (n instanceof JSONObject) {
                JSONObject o = (JSONObject) n;
                if (o.has("is_available")) return Boolean.valueOf(o.optBoolean("is_available", true));
                if (o.has("available")) {
                    Object v = o.opt("available");
                    if (v instanceof Boolean || v instanceof String) return Boolean.valueOf(o.optBoolean("available", true));
                }
                Iterator<String> it = o.keys();
                while (it.hasNext()) {
                    Object v = o.opt(it.next());
                    if (v instanceof JSONObject || v instanceof JSONArray) q.add(v);
                }
            } else if (n instanceof JSONArray) {
                JSONArray a = (JSONArray) n;
                for (int i = 0; i < a.length(); i++) {
                    Object v = a.opt(i);
                    if (v instanceof JSONObject || v instanceof JSONArray) q.add(v);
                }
            }
        }
        return null;
    }

    private static Double num(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return Double.valueOf(((Number) v).doubleValue());
        String s = String.valueOf(v).trim().replace(",", "");
        if (s.isEmpty()) return null;
        try {
            return Double.valueOf(Double.parseDouble(s));
        } catch (Exception e) {
            return null;
        }
    }

    public static String fmt(double d) {
        double a = Math.abs(d);
        if (a - Math.rint(a) < 0.005) return String.format(Locale.US, "%.0f", d);
        return String.format(Locale.US, "%.2f", d);
    }

    static String join(String base, String path) {
        String b = base == null ? "" : base.trim();
        String p = path == null ? "" : path.trim();
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        if (p.isEmpty()) return b;
        if (!p.startsWith("/")) p = "/" + p;
        return b + p;
    }

    private static String read(InputStream is) throws Exception {
        if (is == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        br.close();
        return sb.toString();
    }

    private static String httpError(int code, String body) {
        String brief = brief(body);
        if (code == 401 || code == 403) {
            return "API Key 不对或没有权限（HTTP " + code + "）。\n"
                    + "检查是否复制完整、有没有多余空格，或到官网重新生成一个。\n" + brief;
        }
        if (code == 402) return "账户余额不足或需要付费（HTTP 402）\n" + brief;
        if (code == 404) return "接口地址不存在（HTTP 404）。\n"
                + "多半是「余额接口路径」填错了，可在高级设置里改。\n" + brief;
        if (code == 429) return "请求太频繁了（HTTP 429），把刷新间隔调长一点再试\n" + brief;
        if (code >= 500) return "服务商服务器出错（HTTP " + code + "），过一会儿再试\n" + brief;
        return "请求失败 HTTP " + code + "\n" + brief;
    }

    private static String brief(String body) {
        if (body == null) return "";
        String s = body.replace('\n', ' ').trim();
        if (s.length() > 200) s = s.substring(0, 200) + "...";
        return s;
    }

    private static String netError(Exception e) {
        String m = e.getMessage();
        if (m == null) m = e.getClass().getSimpleName();
        String low = m.toLowerCase(Locale.US);
        if (low.contains("timeout")) return "连接超时，检查手机网络\n" + m;
        if (low.contains("unable to resolve host") || low.contains("unknownhost")) {
            return "域名解析失败，Base URL 可能写错了（高级设置里可改）\n" + m;
        }
        if (low.contains("cleartext")) return "这个地址不允许明文 HTTP，请把 Base URL 换成 https://";
        if (low.contains("connection refused")) return "连接被拒绝，检查 Base URL 是否写错\n" + m;
        return "请求出错\n" + m;
    }
}





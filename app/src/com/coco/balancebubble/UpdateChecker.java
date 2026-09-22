package com.coco.balancebubble;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 从 GitHub 仓库的 Releases 检测新版本。
 *
 * <p>只关心两件事：最新发布的版本号，以及它的下载地址。网络不通、被限流、
 * 返回的 JSON 不认识 —— 一律当成「没查到」，不弹错误、不打扰用户。
 *
 * <p>版本比较与 tag 解析在 {@link Version} 里，是不依赖 Android 的纯函数，有单测；
 * 这一层只负责网络和 JSON，失败一律返回 {@code null}。
 */
public final class UpdateChecker {

    public static final String OWNER = "kekejiang114514";
    public static final String REPO = "token-balance-bubble";
    public static final String API_URL =
            "https://api.github.com/repos/" + OWNER + "/" + REPO + "/releases/latest";
    public static final String PAGE_URL =
            "https://github.com/" + OWNER + "/" + REPO + "/releases";

    private UpdateChecker() {
    }

    /** 一次查询的结果。 */
    public static final class Info {
        public final String tag;      // 原始 tag，例如 v1.7
        public final String version;  // 抠出的版本号，例如 1.7
        public final String notes;    // 发布说明
        public final String url;      // APK 直链；没有资产时退回发布页
        public final boolean direct;  // url 是不是 APK 直链

        Info(String tag, String version, String notes, String url, boolean direct) {
            this.tag = tag;
            this.version = version;
            this.notes = notes == null ? "" : notes;
            this.url = url == null ? PAGE_URL : url;
            this.direct = direct;
        }

        /**
         * 发布说明压成一行，供弹窗显示：把换行和连续空白折成单个空格，去掉
         * Markdown 的井号/星号/反引号（说明是照 CHANGELOG 写的，原样显示会满屏
         * {@code ### 新增} 和 {@code **粗体**}），最后按字符数截断。
         */
        public String shortNotes(int max) {
            String s = notes.replaceAll("[#*`]+", "").replaceAll("\\s+", " ").trim();
            if (max <= 0) return "";
            if (s.length() <= max) return s;
            return s.substring(0, Math.max(0, max - 1)).trim() + "…";
        }
    }

    /** 解析 GitHub 的 release JSON；认不出来返回 null。 */
    public static Info parse(String json) {
        try {
            JSONObject o = new JSONObject(json);
            String tag = o.optString("tag_name", "");
            String version = Version.of(tag);
            if (version.length() == 0) return null;
            String page = o.optString("html_url", PAGE_URL);
            String apk = null;
            JSONArray assets = o.optJSONArray("assets");
            if (assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject a = assets.optJSONObject(i);
                    if (a == null) continue;
                    if (a.optString("name", "").endsWith(".apk")) {
                        apk = a.optString("browser_download_url", "");
                        break;
                    }
                }
            }
            boolean direct = apk != null && apk.length() > 0;
            return new Info(tag, version, o.optString("body", ""), direct ? apk : page, direct);
        } catch (Exception e) {
            return null;
        }
    }

    /** 网络查询。必须在子线程调用；任何失败都返回 null。 */
    public static Info latest() {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(API_URL).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            // GitHub 的接口不带 UA 会直接 403。
            conn.setRequestProperty("User-Agent", "whale-pet-update-check");
            if (conn.getResponseCode() != 200) return null;
            StringBuilder sb = new StringBuilder();
            BufferedReader r = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            return parse(sb.toString());
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}

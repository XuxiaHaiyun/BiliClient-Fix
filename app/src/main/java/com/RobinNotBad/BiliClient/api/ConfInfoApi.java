package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.util.FileUtil;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.ToolsUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Calendar;
import java.util.Map;
import java.util.TreeMap;

/**
 * 被 luern0313 创建于 2019/8/25.
 * (人尽皆知的)绝 · 密 · 档 · 案
 * #以下代码修改自腕上哔哩的开源项目，感谢开源者做出的贡献！
 */

public class ConfInfoApi {


    /*
    这里是WBI签名校验
    https://socialsisteryi.github.io/bilibili-API-collect/docs/misc/sign/wbi.html#wbi-%E7%AD%BE%E5%90%8D%E7%AE%97%E6%B3%95
     */
    private static final int[] MIXIN_KEY_ENC_TAB = {46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
            33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40,
            61, 26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11,
            36, 20, 34, 44, 52};

    // 参与签名的参数值需要先剔除的字符（与 B 站官方算法一致）
    private static final String CHR_FILTER_REGEX = "[!'()*]";

    private static final String SP_KEY_MIXIN = "wbi_mixin_key";
    private static final String SP_KEY_LAST = "last_wbi";

    public static String getWBIRawKey() throws IOException, JSONException {
        JSONObject getJson = NetWorkUtil.getJson("https://api.bilibili.com/x/web-interface/nav");
        // nav 在未登录时会返回 code=-101，但依然携带 data.wbi_img，
        // 因此不能以 code 判定失败，只校验 wbi_img 是否存在。
        JSONObject data = getJson.optJSONObject("data");
        JSONObject wbi_img = data == null ? null : data.optJSONObject("wbi_img");  //不要被名称骗了，这玩意是签名用的
        if (wbi_img == null || wbi_img.isNull("img_url") || wbi_img.isNull("sub_url")) {
            throw new IOException("获取WBI密钥失败: " + getJson.optString("message", "未知错误"));
        }
        String img_key = FileUtil.getFileFirstName(FileUtil.getFileNameFromLink(wbi_img.getString("img_url")));  //得到文件名
        String sub_key = FileUtil.getFileFirstName(FileUtil.getFileNameFromLink(wbi_img.getString("sub_url")));

        return img_key + sub_key;  //相连
    }

    public static String getWBIMixinKey(String raw_key) {
        StringBuilder key = new StringBuilder();
        for (int i = 0; i < 32; i++) {
            key.append(raw_key.charAt(MIXIN_KEY_ENC_TAB[i]));
        }

        return key.toString();
    }

    /**
     * 获取（带缓存的）mixin key。
     * 只有成功获取后才会写入缓存，避免一次失败导致当天所有签名全部失效。
     */
    private static String getWBIMixinKeyCached() throws IOException, JSONException {
        int curr = getDateCurr();
        String cached = SharedPreferencesUtil.getString(SP_KEY_MIXIN, "");
        if (!cached.isEmpty() && SharedPreferencesUtil.getInt(SP_KEY_LAST, 0) == curr) {
            return cached;
        }

        Logu.d("检查WBI");
        String mixinKey = getWBIMixinKey(getWBIRawKey());
        if (mixinKey.length() != 32) {
            throw new IOException("WBI mixin key 长度异常");
        }
        SharedPreferencesUtil.putString(SP_KEY_MIXIN, mixinKey);
        SharedPreferencesUtil.putInt(SP_KEY_LAST, curr);
        return mixinKey;
    }

    /**
     * 对 URL 进行 WBI 签名，返回带 w_rid / wts 的完整 URL。
     * <p>
     * 算法要点：
     * 1. 取出原始 query 参数（先解码，避免二次编码）；
     * 2. 参数值剔除 ! ' ( ) * 后按 encodeURIComponent 语义重新编码；
     * 3. 按 key 排序（含 wts）拼串，追加 mixinKey 后 MD5 得到 w_rid；
     * 4. 最终 URL 使用与签名完全一致的编码串拼接，保证发送内容与签名内容一致。
     */
    public static String signWBI(String url) throws JSONException, IOException {
        String mixinKey = getWBIMixinKeyCached();
        long wts = System.currentTimeMillis() / 1000;

        int qIndex = url.indexOf('?');
        String base = qIndex >= 0 ? url.substring(0, qIndex) : url;
        String rawQuery = qIndex >= 0 ? url.substring(qIndex + 1) : "";

        TreeMap<String, String> params = new TreeMap<>();
        if (!rawQuery.isEmpty()) {
            for (String pair : rawQuery.split("&")) {
                if (pair.isEmpty()) continue;
                int eq = pair.indexOf('=');
                String k = eq >= 0 ? pair.substring(0, eq) : pair;
                String v = eq >= 0 ? pair.substring(eq + 1) : "";
                params.put(k, decode(v));
            }
        }
        params.put("wts", String.valueOf(wts));

        StringBuilder signed = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (signed.length() > 0) signed.append('&');
            signed.append(entry.getKey()).append('=').append(encode(entry.getValue()));
        }

        String calcStr = signed + mixinKey;
        Logu.d("wbi-calc", calcStr);
        String w_rid = ToolsUtil.md5(calcStr);

        return base + "?" + signed + "&w_rid=" + w_rid;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    /**
     * 等价于 JS 的 encodeURIComponent 再剔除 !'()*：
     * 保留 A-Za-z0-9-_.~ ，其余百分号编码，空格编码为 %20。
     */
    private static String encode(String value) {
        String filtered = value.replaceAll(CHR_FILTER_REGEX, "");
        try {
            return URLEncoder.encode(filtered, "UTF-8")
                    .replace("+", "%20")
                    .replace("%7E", "~");
        } catch (UnsupportedEncodingException e) {
            return filtered;
        }
    }

    public static String sortUrlParams(String url) {
        okhttp3.HttpUrl httpUrl = okhttp3.HttpUrl.parse(url);
        String encodedParam = httpUrl == null ? "" : httpUrl.encodedQuery();
        if (encodedParam == null) encodedParam = "";

        TreeMap<String, String> sortedMap = new TreeMap<>();
        for (String param : encodedParam.split("&")) {
            if (param.isEmpty()) continue;
            int eq = param.indexOf('=');
            if (eq >= 0) sortedMap.put(param.substring(0, eq), param.substring(eq + 1));
            else sortedMap.put(param, "");
        }

        StringBuilder sortedUrl = new StringBuilder();
        for (Map.Entry<String, String> entry : sortedMap.entrySet()) {
            if (sortedUrl.length() > 0) sortedUrl.append('&');
            sortedUrl.append(entry.getKey()).append('=').append(entry.getValue());
        }

        return sortedUrl.toString();
    }


    public static int getDateCurr() {
        Calendar calendar = Calendar.getInstance();
        return calendar.get(Calendar.YEAR) * 10000 + (calendar.get(Calendar.MONTH) + 1) * 100 + calendar.get(Calendar.DATE);
    }
}

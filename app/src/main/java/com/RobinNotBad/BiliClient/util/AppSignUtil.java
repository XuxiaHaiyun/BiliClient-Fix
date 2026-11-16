package com.RobinNotBad.BiliClient.util;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * B站 APP 接口签名工具（appkey / appsec）。
 * sign = md5(sortedQuery + appsec)，其中 query 使用 encodeURIComponent 语义编码。
 */
public class AppSignUtil {

    // 移动端 Android
    public static final String APP_KEY = "dfca71928277209b";
    public static final String APP_SEC = "b5475a8825547a4fc26c7d518eaaa02e";

    public static void appSign(Map<String, String> params) {
        appSign(params, APP_KEY, APP_SEC);
    }

    public static void appSign(Map<String, String> params, String appKey, String appSec) {
        params.put("appkey", appKey);
        params.put("ts", String.valueOf(System.currentTimeMillis() / 1000));

        List<String> keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);

        StringBuilder query = new StringBuilder();
        for (String key : keys) {
            String value = params.get(key);
            if (value == null) continue;
            if (query.length() > 0) query.append('&');
            query.append(encodeComponent(key));
            if (!value.isEmpty()) {
                query.append('=').append(encodeComponent(value));
            }
        }

        params.put("sign", ToolsUtil.md5(query + appSec));
    }

    /**
     * 构造 application/x-www-form-urlencoded 请求体（值与签名使用一致的编码）。
     */
    public static String toFormBody(Map<String, String> params) {
        List<String> keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);
        StringBuilder body = new StringBuilder();
        for (String key : keys) {
            String value = params.get(key);
            if (value == null) continue;
            if (body.length() > 0) body.append('&');
            body.append(encodeComponent(key)).append('=').append(encodeComponent(value));
        }
        return body.toString();
    }

    /**
     * 等价于 JS 的 encodeURIComponent：保留 A-Za-z0-9-_.~!'()*，空格编码为 %20。
     */
    public static String encodeComponent(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8")
                    .replace("+", "%20")
                    .replace("%21", "!")
                    .replace("%27", "'")
                    .replace("%28", "(")
                    .replace("%29", ")")
                    .replace("%7E", "~");
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }
}

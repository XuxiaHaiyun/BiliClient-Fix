package com.RobinNotBad.BiliClient.api;

import android.graphics.Bitmap;
import android.util.Log;

import com.RobinNotBad.BiliClient.util.AppSignUtil;
import com.RobinNotBad.BiliClient.util.Cookies;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.QRCodeUtil;
import com.RobinNotBad.BiliClient.util.RsaUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.ToolsUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

import okhttp3.Response;

/**
 * Created by liupe on 2018/10/6.
 * 各位大佬好
 * #以下代码修改自腕上哔哩的开源项目，感谢开源者做出的贡献！
 * <p>
 * 二维码登录逻辑保持原样；新增网页端 / APP 端的密码登录与短信验证码登录（迁移自 PiliPlus）。
 */

public class LoginApi {
    private static String oauthKey;

    private static final String PASSPORT = "https://passport.bilibili.com";
    private static final String APP_USER_AGENT = "Mozilla/5.0 BiliDroid/2.0.1 (bbcallen@gmail.com) os/android model/android_hd mobi_app/android_hd build/2001100 channel/master innerVer/2001100 osVer/15 network/2";
    private static final String TRACE_ID = "11111111111111111111111111111111:1111111111111111:0:0";
    private static final String STATISTICS = "{\"appId\":5,\"platform\":3,\"version\":\"2.0.1\",\"abtest\":\"\"}";
    private static final String DEVICE_ID_KEY = "app_device_id";

    // ==================== 二维码登录（原有逻辑，保持不变） ====================

    public static Bitmap getLoginQR() throws JSONException, IOException {
        String url = "https://passport.bilibili.com/x/passport-login/web/qrcode/generate?source=main-fe-header&go_url=https:%2F%2Fwww.bilibili.com%2F";
        JSONObject loginUrlJson = NetWorkUtil.getJson(url, CookiesApi.genWebHeaders()).getJSONObject("data");
        oauthKey = loginUrlJson.getString("qrcode_key");
        return QRCodeUtil.createQRCodeBitmap(loginUrlJson.getString("url"), 320, 320);
    }

    public static Response getLoginState() throws IOException {
        return NetWorkUtil.get("https://passport.bilibili.com/x/passport-login/web/qrcode/poll?source=main-fe-header&qrcode_key=" + oauthKey, CookiesApi.genWebHeaders());
    }

    public static void requestSSOs() throws JSONException, IOException {
        String listUrl = "https://passport.bilibili.com/x/passport-login/web/sso/list";
        JSONObject listResult = new JSONObject(NetWorkUtil.post(listUrl, new NetWorkUtil.FormData().put("csrf", SharedPreferencesUtil.getString(SharedPreferencesUtil.csrf, "")).toString()).body().string());
        if (listResult.has("data") && !listResult.isNull("data")) {
            JSONArray sso = listResult.getJSONObject("data").getJSONArray("sso");
            for (int i = 0; i < sso.length(); i++) {
                NetWorkUtil.post(sso.getString(i), "");
            }
        }
    }

    // ==================== 公共 ====================

    /**
     * 获取密码加密公钥（网页/APP 共用）。
     *
     * @return 完整响应，成功时 data 含 hash 与 key
     */
    public static JSONObject getWebKey() throws IOException, JSONException {
        return NetWorkUtil.getJson(PASSPORT + "/x/passport-login/web/key", CookiesApi.genWebHeaders());
    }

    private static String getBuvid() {
        Cookies cookies = NetWorkUtil.getCookies();
        String buvid = cookies.getOrDefault("buvid3", "");
        if (buvid.isEmpty()) buvid = cookies.getOrDefault("buvid4", "");
        if (buvid.isEmpty()) {
            buvid = generateRandomHex(32);
            NetWorkUtil.putCookie("buvid3", buvid);
        }
        return buvid;
    }

    private static String getDeviceId() {
        String id = SharedPreferencesUtil.getString(DEVICE_ID_KEY, "");
        if (id.isEmpty()) {
            id = generateRandomHex(16);
            SharedPreferencesUtil.putString(DEVICE_ID_KEY, id);
        }
        return id;
    }

    private static String generateRandomHex(int length) {
        String charset = "0123456789ABCDEF";
        Random random = new Random();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) sb.append(charset.charAt(random.nextInt(charset.length())));
        return sb.toString();
    }

    private static String randomString(int length) {
        String charset = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) sb.append(charset.charAt(random.nextInt(charset.length())));
        return sb.toString();
    }

    private static JSONObject postForm(String url, String body, ArrayList<String> headers) throws IOException, JSONException {
        Response response = NetWorkUtil.post(url, body, headers);
        if (response.body() == null) return new JSONObject();
        return new JSONObject(response.body().string());
    }

    private static ArrayList<String> appHeaders(String buvid) {
        ArrayList<String> headers = new ArrayList<>();
        headers.add("buvid");
        headers.add(buvid);
        headers.add("env");
        headers.add("prod");
        headers.add("app-key");
        headers.add("android_hd");
        headers.add("user-agent");
        headers.add(APP_USER_AGENT);
        headers.add("x-bili-trace-id");
        headers.add(TRACE_ID);
        headers.add("x-bili-aurora-eid");
        headers.add("");
        headers.add("x-bili-aurora-zone");
        headers.add("");
        headers.add("bili-http-engine");
        headers.add("cronet");
        return headers;
    }

    /**
     * 登录成功后的收尾：把 Cookie 中的 mid / csrf 落盘，刷新请求头并同步 SSO。
     * 网页登录时 Cookie 已由拦截器自动写入，这里统一处理。
     */
    private static void finishLogin(JSONObject data) {
        String cookies = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");

        String midStr = NetWorkUtil.getInfoFromCookie("DedeUserID", cookies);
        if (midStr != null && !midStr.isEmpty()) {
            try {
                SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, Long.parseLong(midStr));
            } catch (NumberFormatException ignored) {
            }
        }
        String csrf = NetWorkUtil.getInfoFromCookie("bili_jct", cookies);
        if (csrf != null && !csrf.isEmpty()) SharedPreferencesUtil.putString(SharedPreferencesUtil.csrf, csrf);

        if (data != null) {
            String refreshToken = data.optString("refresh_token", "");
            if (!refreshToken.isEmpty()) SharedPreferencesUtil.putString(SharedPreferencesUtil.refresh_token, refreshToken);
            JSONObject tokenInfo = data.optJSONObject("token_info");
            if (tokenInfo != null) {
                String accessToken = tokenInfo.optString("access_token", "");
                if (!accessToken.isEmpty()) SharedPreferencesUtil.putString(SharedPreferencesUtil.access_key, accessToken);
                String appRefresh = tokenInfo.optString("refresh_token", "");
                if (!appRefresh.isEmpty()) SharedPreferencesUtil.putString(SharedPreferencesUtil.refresh_token, appRefresh);
            }
        }

        SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.cookie_refresh, true);
        NetWorkUtil.refreshHeaders();
        try {
            requestSSOs();
        } catch (Exception e) {
            Log.e("LoginApi", "requestSSOs 失败", e);
        }
    }

    /**
     * APP 登录成功后，把响应里的 cookie_info 写入 Cookie 存储。
     */
    private static void writeAppCookies(JSONObject data) {
        if (data == null) return;
        JSONObject cookieInfo = data.optJSONObject("cookie_info");
        if (cookieInfo == null) return;
        JSONArray cookies = cookieInfo.optJSONArray("cookies");
        if (cookies == null) return;
        for (int i = 0; i < cookies.length(); i++) {
            JSONObject cookie = cookies.optJSONObject(i);
            if (cookie == null) continue;
            NetWorkUtil.putCookie(cookie.optString("name"), cookie.optString("value"));
        }
    }

    // ==================== 网页端密码 / 短信登录 ====================

    /**
     * 网页端密码登录。
     *
     * @return 完整响应；code==0 表示成功（Cookie 已自动落盘）
     */
    public static JSONObject loginByPasswordWeb(String username, String password) throws Exception {
        JSONObject keyResp = getWebKey();
        JSONObject keyData = keyResp.optJSONObject("data");
        if (keyData == null) throw new IOException("获取加密公钥失败：" + keyResp.optString("message"));

        String hash = keyData.getString("hash");
        String key = keyData.getString("key");
        String encrypted = RsaUtil.encryptByPublicKey(key, hash + password);

        String url = PASSPORT + "/x/passport-login/web/login";
        String body = new NetWorkUtil.FormData()
                .put("username", username)
                .put("password", encrypted)
                .put("keep", 0)
                .put("csrf", SharedPreferencesUtil.getString(SharedPreferencesUtil.csrf, ""))
                .put("source", "main-fe-header")
                .put("go_url", "https://www.bilibili.com/")
                .put("timestamp", System.currentTimeMillis())
                .toString();

        JSONObject result = postForm(url, body, CookiesApi.genWebHeaders());
        if (result.optInt("code", -1) == 0) finishLogin(result.optJSONObject("data"));
        return result;
    }

    /**
     * 网页端发送短信验证码。
     *
     * @return 完整响应；成功时 data.captcha_key 用于后续登录
     */
    public static JSONObject sendSmsCodeWeb(String tel) throws IOException, JSONException {
        String url = PASSPORT + "/x/passport-login/web/sms/send" + new NetWorkUtil.FormData()
                .setUrlParam(true)
                .put("cid", 86)
                .put("tel", tel)
                .put("csrf", SharedPreferencesUtil.getString(SharedPreferencesUtil.csrf, ""))
                .put("source", "main_web");
        Response response = NetWorkUtil.get(url, CookiesApi.genWebHeaders());
        if (response.body() == null) return new JSONObject();
        return new JSONObject(response.body().string());
    }

    /**
     * 网页端短信验证码登录。
     */
    public static JSONObject loginBySmsWeb(String tel, String code, String captchaKey) throws IOException, JSONException {
        String url = PASSPORT + "/x/passport-login/web/login/sms";
        String body = new NetWorkUtil.FormData()
                .put("cid", 86)
                .put("tel", tel)
                .put("code", code)
                .put("csrf", SharedPreferencesUtil.getString(SharedPreferencesUtil.csrf, ""))
                .put("source", "main_web")
                .put("keep", 0)
                .put("captcha_key", captchaKey == null ? "" : captchaKey)
                .toString();

        JSONObject result = postForm(url, body, CookiesApi.genWebHeaders());
        if (result.optInt("code", -1) == 0) finishLogin(result.optJSONObject("data"));
        return result;
    }

    // ==================== APP 端密码 / 短信登录 ====================

    /**
     * APP 端密码登录。
     */
    public static JSONObject loginByPasswordApp(String username, String password) throws Exception {
        JSONObject keyResp = getWebKey();
        JSONObject keyData = keyResp.optJSONObject("data");
        if (keyData == null) throw new IOException("获取加密公钥失败：" + keyResp.optString("message"));

        String hash = keyData.getString("hash");
        String key = keyData.getString("key");
        String buvid = getBuvid();
        String deviceId = getDeviceId();

        String passwordEncrypted = RsaUtil.encryptByPublicKey(key, hash + password);
        String dt = AppSignUtil.encodeComponent(RsaUtil.encryptByPublicKey(key, randomString(16)));

        Map<String, String> params = new LinkedHashMap<>();
        params.put("bili_local_id", deviceId);
        params.put("build", "2001100");
        params.put("buvid", buvid);
        params.put("c_locale", "zh_CN");
        params.put("channel", "master");
        params.put("device", "phone");
        params.put("device_id", deviceId);
        params.put("device_name", "vivo");
        params.put("device_platform", "Android14vivo");
        params.put("disable_rcmd", "0");
        params.put("dt", dt);
        params.put("from_pv", "main.homepage.avatar-nologin.all.click");
        params.put("from_url", AppSignUtil.encodeComponent("bilibili://pegasus/promo"));
        params.put("local_id", buvid);
        params.put("mobi_app", "android_hd");
        params.put("password", passwordEncrypted);
        params.put("permission", "ALL");
        params.put("platform", "android");
        params.put("s_locale", "zh_CN");
        params.put("statistics", STATISTICS);
        params.put("username", username);

        AppSignUtil.appSign(params);

        JSONObject result = postForm(PASSPORT + "/x/passport-login/oauth2/login",
                AppSignUtil.toFormBody(params), appHeaders(buvid));
        if (result.optInt("code", -1) == 0) {
            JSONObject data = result.optJSONObject("data");
            writeAppCookies(data);
            finishLogin(data);
        }
        return result;
    }

    /**
     * APP 端发送短信验证码。
     */
    public static JSONObject sendSmsCodeApp(String tel) throws IOException, JSONException {
        String buvid = getBuvid();
        long timestamp = System.currentTimeMillis();

        Map<String, String> params = new LinkedHashMap<>();
        params.put("build", "2001100");
        params.put("buvid", buvid);
        params.put("c_locale", "zh_CN");
        params.put("channel", "master");
        params.put("cid", "86");
        params.put("disable_rcmd", "0");
        params.put("local_id", buvid);
        params.put("login_session_id", ToolsUtil.md5(buvid + timestamp));
        params.put("mobi_app", "android_hd");
        params.put("platform", "android");
        params.put("s_locale", "zh_CN");
        params.put("statistics", STATISTICS);
        params.put("tel", tel);

        AppSignUtil.appSign(params);

        return postForm(PASSPORT + "/x/passport-login/sms/send",
                AppSignUtil.toFormBody(params), appHeaders(buvid));
    }

    /**
     * APP 端短信验证码登录。
     */
    public static JSONObject loginBySmsApp(String tel, String code, String captchaKey) throws Exception {
        JSONObject keyResp = getWebKey();
        JSONObject keyData = keyResp.optJSONObject("data");
        if (keyData == null) throw new IOException("获取加密公钥失败：" + keyResp.optString("message"));

        String key = keyData.getString("key");
        String buvid = getBuvid();
        String deviceId = getDeviceId();

        String dt = AppSignUtil.encodeComponent(RsaUtil.encryptByPublicKey(key, randomString(16)));

        Map<String, String> params = new LinkedHashMap<>();
        params.put("bili_local_id", deviceId);
        params.put("build", "2001100");
        params.put("buvid", buvid);
        params.put("c_locale", "zh_CN");
        params.put("captcha_key", captchaKey == null ? "" : captchaKey);
        params.put("channel", "master");
        params.put("cid", "86");
        params.put("code", code);
        params.put("device", "phone");
        params.put("device_id", deviceId);
        params.put("device_name", "vivo");
        params.put("device_platform", "Android14vivo");
        params.put("disable_rcmd", "0");
        params.put("dt", dt);
        params.put("from_pv", "main.my-information.my-login.0.click");
        params.put("from_url", AppSignUtil.encodeComponent("bilibili://user_center/mine"));
        params.put("local_id", buvid);
        params.put("mobi_app", "android_hd");
        params.put("platform", "android");
        params.put("s_locale", "zh_CN");
        params.put("statistics", STATISTICS);
        params.put("tel", tel);

        AppSignUtil.appSign(params);

        JSONObject result = postForm(PASSPORT + "/x/passport-login/login/sms",
                AppSignUtil.toFormBody(params), appHeaders(buvid));
        if (result.optInt("code", -1) == 0) {
            JSONObject data = result.optJSONObject("data");
            writeAppCookies(data);
            finishLogin(data);
        }
        return result;
    }

}

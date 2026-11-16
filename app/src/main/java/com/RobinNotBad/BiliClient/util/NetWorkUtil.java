package com.RobinNotBad.BiliClient.util;

import android.annotation.SuppressLint;
import android.os.Build;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.Inflater;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.Dns;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 被 luern0313 创建于 2019/10/13.
 * #以下代码来源于腕上哔哩的开源项目，感谢开源者做出的贡献！
 */

public class NetWorkUtil {
    private static final AtomicReference<OkHttpClient> INSTANCE = new AtomicReference<>();

    public static class Inet4Selector implements Dns {
        private static final ExecutorService DNS_EXECUTOR = Executors.newCachedThreadPool();
        private static final String[] FALLBACK_DNS = {"223.5.5.5", "119.29.29.29", "114.114.114.114"};

        @NonNull
        @Override
        public List<InetAddress> lookup(@NonNull String hostname) throws UnknownHostException {
            List<InetAddress> hosts = systemLookup(hostname);
            List<InetAddress> inet4Hosts = new ArrayList<>();
            if (hosts != null) {
                for (InetAddress host : hosts) {
                    if (host.getAddress().length == 4) inet4Hosts.add(host);
                }
            }
            Logu.e("dns", hostname + " system=" + hosts + " ipv4=" + inet4Hosts);
            if (!inet4Hosts.isEmpty()) return inet4Hosts;    //筛选IPV4地址，IPV6请求有异常

            // 系统解析失败或超时（部分路由器/运营商 DNS 对个别域名会返回 NODATA 或长时间无响应），
            // 直接向公共 DNS 发起 A 记录查询兜底，避免分享短链等场景直接失败。
            for (String server : FALLBACK_DNS) {
                List<InetAddress> fallback = resolveByUdpDns(hostname, server);
                if (fallback != null && !fallback.isEmpty()) {
                    Logu.e("dns-fallback", hostname + " -> " + fallback + " via " + server);
                    return fallback;
                }
            }
            if (hosts != null && !hosts.isEmpty()) return hosts;
            throw new UnknownHostException("Unable to resolve host \"" + hostname + "\"");
        }

        private static List<InetAddress> systemLookup(String hostname) {
            Future<List<InetAddress>> future = DNS_EXECUTOR.submit(() -> Dns.SYSTEM.lookup(hostname));
            try {
                return future.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                future.cancel(true);
                Logu.e("dns-timeout", hostname + " : " + e);
                return null;
            }
        }

        /**
         * 极简 DNS A 记录查询（UDP）。仅用于系统解析不可用时的兜底。
         */
        private static List<InetAddress> resolveByUdpDns(String hostname, String dnsServer) {
            DatagramSocket socket = null;
            try {
                byte[] query = buildDnsQuery(hostname);
                socket = new DatagramSocket();
                socket.setSoTimeout(4000);
                socket.send(new DatagramPacket(query, query.length, InetAddress.getByName(dnsServer), 53));

                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                return parseDnsResponse(packet.getData(), packet.getLength());
            } catch (Exception e) {
                return null;
            } finally {
                if (socket != null) socket.close();
            }
        }

        private static byte[] buildDnsQuery(String hostname) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(0x12);
            out.write(0x34);   // ID
            out.write(0x01);
            out.write(0x00);   // Flags: 标准查询 + 递归
            out.write(0x00);
            out.write(0x01);   // QDCOUNT
            out.write(0x00);
            out.write(0x00);   // ANCOUNT
            out.write(0x00);
            out.write(0x00);   // NSCOUNT
            out.write(0x00);
            out.write(0x00);   // ARCOUNT
            for (String label : hostname.split("\\.")) {
                byte[] bytes = label.getBytes();
                out.write(bytes.length);
                out.write(bytes, 0, bytes.length);
            }
            out.write(0x00);   // QNAME 结束
            out.write(0x00);
            out.write(0x01);   // QTYPE A
            out.write(0x00);
            out.write(0x01);   // QCLASS IN
            return out.toByteArray();
        }

        private static List<InetAddress> parseDnsResponse(byte[] data, int length) throws UnknownHostException {
            if (length < 12) return null;
            int qdCount = ((data[4] & 0xFF) << 8) | (data[5] & 0xFF);
            int anCount = ((data[6] & 0xFF) << 8) | (data[7] & 0xFF);
            int offset = 12;
            for (int i = 0; i < qdCount; i++) {
                offset = skipName(data, offset);
                offset += 4;
            }
            List<InetAddress> result = new ArrayList<>();
            for (int i = 0; i < anCount && offset + 10 <= length; i++) {
                offset = skipName(data, offset);
                int type = ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
                int rdLength = ((data[offset + 8] & 0xFF) << 8) | (data[offset + 9] & 0xFF);
                offset += 10;
                if (type == 1 && rdLength == 4 && offset + 4 <= length) {
                    result.add(InetAddress.getByAddress(new byte[]{data[offset], data[offset + 1], data[offset + 2], data[offset + 3]}));
                }
                offset += rdLength;
            }
            return result;
        }

        private static int skipName(byte[] data, int offset) {
            while (offset < data.length) {
                int len = data[offset] & 0xFF;
                if (len == 0) return offset + 1;
                if ((len & 0xC0) == 0xC0) return offset + 2;
                offset += len + 1;
            }
            return offset;
        }
    }

    public static OkHttpClient getOkHttpInstance() {
        while (INSTANCE.get() == null) {
            INSTANCE.compareAndSet(null, setOkHttpSsl(new OkHttpClient.Builder())
                    .followRedirects(false)
                    .addInterceptor(chain -> {
                        Request request = chain.request();
                        Response response = chain.proceed(request);
                        RedirectHandler handler;
                        String location = response.header("Location");
                        boolean isSslRedirect = false;
                        try {
                            isSslRedirect = location != null && !request.isHttps() && new URI(location).getScheme().equalsIgnoreCase("https") && request.url().host().equalsIgnoreCase(new URI(location).getHost());
                        } catch (URISyntaxException ignored) {
                        }

                        Logu.e("redirect-check", "host=" + request.url().host() + " code=" + response.code() + " isRedirect=" + response.isRedirect() + " location=" + location + " handler=" + (request.tag(RedirectHandler.class) != null) + " isSslRedirect=" + isSslRedirect);

                        if (response.isRedirect() && location != null) {
                            if (request.url().host().equals("b23.tv") && !isSslRedirect && (handler = request.tag(RedirectHandler.class)) != null) {
                                handler.handleRedirect(location);
                            } else {
                                Request newRequest = request.newBuilder()
                                        .url(location)
                                        .build();
                                return chain.proceed(newRequest);
                            }
                        }
                        return response;
                    })
                    .addInterceptor(new CookieSaveInterceptor())
                    .dns(new Inet4Selector())
                    .pingInterval(8, TimeUnit.SECONDS)
                    .connectTimeout(8, TimeUnit.SECONDS)
                    .readTimeout(16, TimeUnit.SECONDS).build());
        }
        return INSTANCE.get();
    }

    public synchronized static OkHttpClient.Builder setOkHttpSsl(OkHttpClient.Builder okhttpBuilder) {
        if (Build.VERSION.SDK_INT > 22) return okhttpBuilder;
        try {
            @SuppressLint("CustomX509TrustManager") final X509TrustManager trustAllCert =
                    new X509TrustManager() {
                        @SuppressLint("TrustAllX509TrustManager")
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                        }

                        @SuppressLint("TrustAllX509TrustManager")
                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[]{};
                        }
                    };
            final SSLSocketFactory sslSocketFactory = new SSLSocketFactoryCompat(trustAllCert);
            okhttpBuilder.sslSocketFactory(sslSocketFactory, trustAllCert);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return okhttpBuilder;
    }

    public static JSONObject getJson(String url) throws IOException, JSONException {
        return getJson(url, webHeaders);
    }

    public static JSONObject getJson(String url, ArrayList<String> headers) throws IOException, JSONException {
        // 添加重试机制
        int retryCount = 0;
        final int maxRetries = 3;
        
        while (retryCount < maxRetries) {
            Response response = get(url, headers);
            try (ResponseBody body = response.body()) {
                if (body != null) {
                    String responseText = body.string();
                    
                    // 检查是否是HTML页面
                    if (responseText.trim().startsWith("<!DOCTYPE") || responseText.trim().startsWith("<html")) {
                        Logu.e("收到HTML页面而不是JSON数据，重试 " + (retryCount + 1) + "/" + maxRetries + " code=" + response.code() + " head=" + response.headers().toString() + " body=" + responseText.substring(0, Math.min(300, responseText.length())) + " : " + url);
                        retryCount++;
                        if (retryCount < maxRetries) {
                            Thread.sleep(1000); // 等待1秒后重试
                            continue;
                        } else {
                            // 所有重试都失败，返回一个包含错误信息的JSONObject
                            // 而不是抛出异常，这样上层可以统一处理
                            JSONObject errorJson = new JSONObject();
                            errorJson.put("code", -1000); // 自定义错误码
                            errorJson.put("message", "网络请求失败，请检查网络连接后重试");
                            errorJson.put("data", new JSONObject());
                            errorJson.put("retry_failed", true);
                            errorJson.put("original_url", url);
                            return errorJson;
                        }
                    }
                    
                    // 检查响应是否为空或太短（可能是错误页面）
                    if (responseText.trim().isEmpty() || responseText.length() < 10) {
                        Logu.e("响应数据过短，重试 " + (retryCount + 1) + "/" + maxRetries + ": " + url);
                        retryCount++;
                        if (retryCount < maxRetries) {
                            Thread.sleep(1000);
                            continue;
                        } else {
                            // 所有重试都失败，返回错误JSON
                            JSONObject errorJson = new JSONObject();
                            errorJson.put("code", -1001);
                            errorJson.put("message", "服务器响应异常，请稍后重试");
                            errorJson.put("data", new JSONObject());
                            errorJson.put("retry_failed", true);
                            errorJson.put("original_url", url);
                            return errorJson;
                        }
                    }
                    
                    // 尝试解析JSON
                    JSONObject json = new JSONObject(responseText);
                    
                    // 检查是否是B站API的标准响应格式
                    if (json.has("code")) {
                        int code = json.optInt("code", -1);
                        if (code == 0) {
                            // 成功响应
                            return json;
                        } else {
                            // B站API返回了错误码，直接返回这个JSON
                            // 上层应该检查code字段
                            return json;
                        }
                    }
                    
                    // 如果不是标准格式，直接返回
                    return json;
                } else {
                    // 响应体为空
                    JSONObject errorJson = new JSONObject();
                    errorJson.put("code", -1002);
                    errorJson.put("message", "服务器无响应，请检查网络连接");
                    errorJson.put("data", new JSONObject());
                    errorJson.put("retry_failed", true);
                    errorJson.put("original_url", url);
                    return errorJson;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("线程被中断", e);
            } catch (JSONException e) {
                // JSON解析异常，检查是否需要重试
                if (retryCount < maxRetries - 1) {
                    Logu.e("JSON解析失败，重试 " + (retryCount + 1) + "/" + maxRetries + ": " + e.getMessage() + " - " + url);
                    retryCount++;
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("线程被中断", ie);
                    }
                    continue;
                } else {
                    // 所有重试都失败，返回错误JSON
                    JSONObject errorJson = new JSONObject();
                    errorJson.put("code", -1003);
                    errorJson.put("message", "数据解析失败，请稍后重试");
                    errorJson.put("data", new JSONObject());
                    errorJson.put("retry_failed", true);
                    errorJson.put("original_url", url);
                    errorJson.put("json_error", e.getMessage());
                    return errorJson;
                }
            } catch (Exception e) {
                // 其他异常，检查是否需要重试
                if (retryCount < maxRetries - 1) {
                    Logu.e("网络请求失败，重试 " + (retryCount + 1) + "/" + maxRetries + ": " + e.getMessage() + " - " + url);
                    retryCount++;
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("线程被中断", ie);
                    }
                    continue;
                } else {
                    // 所有重试都失败，返回错误JSON
                    JSONObject errorJson = new JSONObject();
                    errorJson.put("code", -1004);
                    errorJson.put("message", "网络请求异常: " + e.getMessage());
                    errorJson.put("data", new JSONObject());
                    errorJson.put("retry_failed", true);
                    errorJson.put("original_url", url);
                    return errorJson;
                }
            }
        }
        
        // 理论上不会执行到这里，因为所有路径都有返回
        JSONObject errorJson = new JSONObject();
        errorJson.put("code", -9999);
        errorJson.put("message", "未知错误");
        errorJson.put("data", new JSONObject());
        errorJson.put("retry_failed", true);
        errorJson.put("original_url", url);
        return errorJson;
    }

    public static Response get(String url) throws IOException {
        return get(url, webHeaders);
    }

    public static Response get(String url, ArrayList<String> headers) throws IOException {
        return get(url, headers, null);
    }

    public static Response get(String url, ArrayList<String> headers, RedirectHandler redirectHandler) throws IOException {
        Logu.d("get-url", url);
        OkHttpClient client = getOkHttpInstance();
        Request.Builder requestBuilder = new Request.Builder().url(url).get();
        for (int i = 0; i < headers.size(); i += 2)
            requestBuilder.addHeader(headers.get(i), headers.get(i + 1));
        if (redirectHandler != null) requestBuilder.tag(RedirectHandler.class, redirectHandler);
        Request request = requestBuilder.build();
        return client.newCall(request).execute();
    }

    public static Response post(String url, String data, List<String> headers, String contentType) throws IOException {
        Logu.d("post-url", url);
        Logu.d("post-data", data);
        OkHttpClient client = getOkHttpInstance();
        RequestBody body = RequestBody.create(MediaType.parse(contentType + "; charset=utf-8"), data);
        Request.Builder requestBuilder = new Request.Builder().url(url).post(body);
        for (int i = 0; i < headers.size(); i += 2) {
            String key = headers.get(i);
            String val = headers.get(i + 1);
            if (key.equalsIgnoreCase("Content-Type")) val = contentType;
            requestBuilder.addHeader(key, val);
        }
        Request request = requestBuilder.build();
        return client.newCall(request).execute();
    }

    public static Response post(String url, String data, List<String> headers) throws IOException {
        return post(url, data, headers, "application/x-www-form-urlencoded");
    }

    public static Response postJson(String url, String data, List<String> headers) throws IOException {
        return post(url, data, headers, "application/json");
    }

    public static Response postJson(String url, String data) throws IOException {
        return post(url, data, webHeaders, "application/json");
    }

    public static Response post(String url, String data) throws IOException {
        return post(url, data, webHeaders);
    }


    public static byte[] readStream(InputStream inStream) throws IOException {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = inStream.read(buffer)) != -1) {
            outStream.write(buffer, 0, len);
        }
        outStream.close();
        inStream.close();
        return outStream.toByteArray();
    }

    public static byte[] uncompress(byte[] inputByte) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(inputByte.length);
        try {
            Inflater inflater = new Inflater(true);
            inflater.setInput(inputByte);
            byte[] buffer = new byte[4 * 1024];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                outputStream.write(buffer, 0, count);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        byte[] output = outputStream.toByteArray();
        outputStream.close();
        return output;
    }

    public static String getInfoFromCookie(String name, String cookie) {
        String[] cookies = cookie.split("; ");
        for (String i : cookies) {
            if (i.contains(name + "="))
                return i.substring(name.length() + 1);
        }
        return "";
    }

    /**
     * 读取存储的 Cookie 并规范化为合法请求头。
     * 历史版本可能写入尾部多余的 "; "，会让 B 站风控直接返回 412，这里统一清理。
     */
    public static String getCookieString() {
        String cookies = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");
        if (cookies == null) return "";
        StringBuilder sb = new StringBuilder();
        for (String part : cookies.split(";")) {
            String item = part.trim();
            if (item.isEmpty()) continue;
            if (sb.length() > 0) sb.append("; ");
            sb.append(item);
        }
        return sb.toString();
    }

    private static void saveCookiesFromResponse(Response response) {
        List<String> newCookies = response.headers("Set-Cookie");

        //如果没有新cookies，直接返回
        if (newCookies.isEmpty()) return;
        String cookiesStr = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");
        ArrayList<String> oldCookies = new ArrayList<>();  //转list
        for (String item : cookiesStr.split("; ")) {
            if (!item.trim().isEmpty()) oldCookies.add(item.trim());
        }

        for (String newCookie : newCookies) {  //对每一条新cookie遍历

            Cookies cookies = new Cookies(newCookie);
            if (cookies.containsKey("Domain") && !cookies.get("Domain").endsWith("bilibili.com"))
                continue;

            int index = newCookie.indexOf("; ");
            if (index != -1) newCookie = newCookie.substring(0, index);  //如果没有分号不做处理

            index = newCookie.indexOf("=") + 1;
            if (index == 0) continue;   //如果没有等号，跳过

            String key = newCookie.substring(0, index);    //key=
            Logu.d("newCookie", newCookie);

            boolean added = false;
            for (int i = 0; i < oldCookies.size(); i++) {  //查找旧cookie表有没有
                String oldCookie = oldCookies.get(i);
                if (oldCookie.contains(key)) {
                    oldCookies.set(i, newCookie);    //有的话直接换掉
                    added = true;
                    break;
                }
            }
            if (!added) {
                oldCookies.add(newCookie);  //没有就加项
            }
        }

        StringBuilder setCookies = new StringBuilder();
        for (String setCookie : oldCookies) {
            setCookies.append(setCookie).append("; ");
        }
        //如果一次setCookies都没有，就不要存了， 因为是个空字符串
        if (setCookies.length() >= 2) {
            Logu.d("save-result", setCookies.substring(0, setCookies.length() - 2));
            SharedPreferencesUtil.putString(SharedPreferencesUtil.cookies, setCookies.substring(0, setCookies.length() - 2));
            refreshHeaders();
        }
    }

    /**
     * 存储单个Cookie
     *
     * @param key 键
     * @param val 值
     */
    public static void putCookie(String key, String val) {
        synchronized (NetWorkUtil.class) {
            Cookies cookies = new Cookies(SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""));
            cookies.set(key, val);
            SharedPreferencesUtil.putString(SharedPreferencesUtil.cookies, cookies.toString());
            refreshHeaders();
        }
    }

    /**
     * 存储Cookies（覆盖写入）
     *
     * @param cookies cookies
     */
    public static void setCookies(Cookies cookies) {
        synchronized (NetWorkUtil.class) {
            SharedPreferencesUtil.putString(SharedPreferencesUtil.cookies, cookies.toString());
            refreshHeaders();
        }
    }

    /**
     * 获取存储的Cookies
     *
     * @return 存储的Cookies
     */
    public static Cookies getCookies() {
        synchronized (NetWorkUtil.class) {
            return new Cookies(SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""));
        }
    }

    public static final String USER_AGENT_WEB = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.95 Safari/537.36";
    public static final String USER_AGENT_APP = "Mozilla/5.0 BiliDroid/8.43.0 (bbcallen@gmail.com) os/android model/android mobi_app/android build/8430300 channel/master innerVer/8430300 osVer/15 network/2";
    public static final ArrayList<String> webHeaders = new ArrayList<>() {{
        add("Cookie");
        add(getCookieString());

        add("Origin");
        add("https://www.bilibili.com");

        add("Referer");
        add("https://www.bilibili.com/");

        add("User-Agent");
        add(USER_AGENT_WEB);

        add("Sec-Ch-Ua");
        add("\"Chromium\";v=\"122\", \"Not(A:Brand\";v=\"24\", \"Google Chrome\";v=\"122\"");

        add("Sec-Ch-Ua-Platform");
        add("\"Windows\"");

        add("Sec-Ch-Ua-Mobile");
        add("?0");
    }};

    // 部分接口（如用户空间稿件）对来源页校验严格，使用APP端UA与标头可避免未登录时的风控
    public static final ArrayList<String> appHeaders = new ArrayList<>() {{
        add("Cookie");
        add(getCookieString());

        add("User-Agent");
        add(USER_AGENT_APP);

        add("bili-http-engine");
        add("cronet");

        add("env");
        add("prod");

        add("app-key");
        add("android64");

        add("x-bili-aurora-zone");
        add("sh001");
    }};

    public static void refreshHeaders() {
        webHeaders.set(1, getCookieString());
        appHeaders.set(1, getCookieString());
    }

    /**
     * 复制一份 webHeaders 并移除 Cookie。
     * 部分接口（如未登录拉评论 /x/v2/reply/main）带 Cookie 反而会触发风控，
     * PiliPlus 对未登录评论请求显式置空 cookie，这里保持一致。
     */
    public static ArrayList<String> webHeadersNoCookie() {
        ArrayList<String> headers = new ArrayList<>(webHeaders);
        for (int i = headers.size() - 2; i >= 0; i -= 2) {
            if (headers.get(i).equalsIgnoreCase("Cookie")) {
                headers.remove(i + 1);
                headers.remove(i);
            }
        }
        return headers;
    }

    /**
     * 复制一份 webHeaders 并替换 Referer，用于少数对来源页有要求的接口（如搜索）。
     */
    public static ArrayList<String> webHeadersWithReferer(String referer) {
        ArrayList<String> headers = new ArrayList<>(webHeaders);
        for (int i = 0; i + 1 < headers.size(); i += 2) {
            if (headers.get(i).equalsIgnoreCase("Referer")) {
                headers.set(i + 1, referer);
                break;
            }
        }
        return headers;
    }

    /**
     * 复制一份 webHeaders 并替换 Referer 与 Origin，用于搜索等对来源页有严格要求的接口。
     */
    public static ArrayList<String> webHeadersWithRefererAndOrigin(String referer, String origin) {
        ArrayList<String> headers = new ArrayList<>(webHeaders);
        for (int i = 0; i + 1 < headers.size(); i += 2) {
            String key = headers.get(i);
            if (key.equalsIgnoreCase("Referer")) headers.set(i + 1, referer);
            else if (key.equalsIgnoreCase("Origin")) headers.set(i + 1, origin);
        }
        return headers;
    }

    public static class FormData {
        private final Map<String, String> data;
        private boolean isUrlParam;

        public FormData() {
            data = new HashMap<>();
        }

        public FormData remove(String key) {
            data.remove(key);
            return this;
        }

        public FormData put(String key, Object value) {
            data.put(key, String.valueOf(value));
            return this;
        }

        public FormData setUrlParam(boolean isUrlParam) {
            this.isUrlParam = isUrlParam;
            return this;
        }

        @NonNull
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();

            if (isUrlParam) sb.append("?");

            try {
                for (String key : data.keySet()) {
                    if (sb.length() > (isUrlParam ? 1 : 0)) {
                        sb.append("&");
                    }
                    sb.append(URLEncoder.encode(key, "UTF-8"));
                    sb.append("=");
                    sb.append(URLEncoder.encode(data.get(key), "UTF-8"));
                }
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }

            return sb.toString();
        }
    }

    public interface RedirectHandler {
        void handleRedirect(String location);
    }

    private static class CookieSaveInterceptor implements Interceptor {
        @NonNull
        @Override
        public Response intercept(Chain chain) throws IOException {
            Response response = chain.proceed(chain.request());
            saveCookiesFromResponse(response);
            return response;
        }
    }

}

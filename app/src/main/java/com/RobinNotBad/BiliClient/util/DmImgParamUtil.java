package com.RobinNotBad.BiliClient.util;

import android.util.Base64;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

import okhttp3.HttpUrl;

public class DmImgParamUtil {

    // 参考 PiliPlus：dm_img_str / dm_cover_img_str 使用随机base64，dm_img_list / dm_img_inter 留空。
    // 之前伪造的鼠标轨迹数据会被风控识别，导致接口返回HTML而非JSON。
    public static Map<String, String> getDmImgParams() {
        HashMap<String, String> map = new HashMap<>();
        map.put("dm_img_str", randomBase64String(16, 64));
        map.put("dm_cover_img_str", randomBase64String(32, 128));
        map.put("dm_img_list", "[]");
        map.put("dm_img_inter", "{\"ds\":[],\"wh\":[0,0,0],\"of\":[0,0,0]}");
        return map;
    }

    public static String getDmImgParamsUrl(String orig_url) {
        HttpUrl.Builder builder = Objects.requireNonNull(HttpUrl.parse(orig_url)).newBuilder();
        Map<String, String> params = getDmImgParams();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            builder.addQueryParameter(entry.getKey(), entry.getValue());
        }
        return builder.build().toString();
    }

    private static String randomBase64String(int minLength, int maxLength) {
        Random random = new Random();
        int length = random.nextInt(maxLength - minLength + 1) + minLength;
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            // 生成可打印ASCII，且避开'%'（0x25），避免URL编码问题
            bytes[i] = (byte) (0x26 + random.nextInt(0x59));
        }
        String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
        return base64.substring(0, base64.length() - 2);
    }
}

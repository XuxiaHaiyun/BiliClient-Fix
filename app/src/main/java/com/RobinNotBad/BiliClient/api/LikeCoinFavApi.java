package com.RobinNotBad.BiliClient.api;

import android.util.Log;

import com.RobinNotBad.BiliClient.model.ApiResult;
import com.RobinNotBad.BiliClient.model.VideoInfo;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;

public class LikeCoinFavApi {

    private static ArrayList<String> videoHeaders(long aid) {
        return NetWorkUtil.webHeadersWithReferer("https://www.bilibili.com/video/av" + aid);
    }

    public static int triple(long aid) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/web-interface/archive/like/triple";
        String per = new NetWorkUtil.FormData()
                .put("aid", aid)
                .put("eab_x", 2)
                .put("ramval", 0)
                .put("source", "web_normal")
                .put("ga", 1)
                .put("csrf", SharedPreferencesUtil.getString(SharedPreferencesUtil.csrf, ""))
                .put("spmid", "333.788.0.0")
                .put("statistics", "{\"appId\":100,\"platform\":5}")
                .toString();

        JSONObject result = new JSONObject(Objects.requireNonNull(NetWorkUtil.post(url, per, videoHeaders(aid)).body()).string());
        Log.e("debug-三联", result.toString());
        return result.getInt("code");
    }

    /**
     * 点赞 / 取消点赞
     *
     * @param likeState 1：点赞 2：取消赞
     */
    public static int like(long aid, int likeState) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/web-interface/archive/like";
        String per = new NetWorkUtil.FormData()
                .put("aid", aid)
                .put("like", likeState)
                .put("csrf", SharedPreferencesUtil.getString(SharedPreferencesUtil.csrf, ""))
                .toString();

        JSONObject result = new JSONObject(Objects.requireNonNull(NetWorkUtil.post(url, per, videoHeaders(aid)).body()).string());
        Log.e("debug-点赞", result.toString());
        return result.getInt("code");
    }

    public static int coin(long aid, int multiply) throws IOException, JSONException {
        return coin(aid, multiply, 0);
    }

    /**
     * 投币
     *
     * @param multiply   投币数量，上限2
     * @param selectLike 是否同时点赞 0：否 1：是
     */
    public static int coin(long aid, int multiply, int selectLike) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/web-interface/coin/add";
        String per = new NetWorkUtil.FormData()
                .put("aid", aid)
                .put("multiply", multiply)
                .put("select_like", selectLike)
                .put("csrf", SharedPreferencesUtil.getString(SharedPreferencesUtil.csrf, ""))
                .toString();

        JSONObject result = new JSONObject(Objects.requireNonNull(NetWorkUtil.post(url, per, videoHeaders(aid)).body()).string());
        Log.e("debug-投币", result.toString());
        return result.getInt("code");
    }

    /**
     * 收藏视频
     *
     * @param fid 收藏夹 media_id（即 fid 本身，v3 接口不需要拼接 mid 后缀）
     */
    public static int favorite(long aid, long fid) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/v3/fav/resource/deal";
        String per = new NetWorkUtil.FormData()
                .put("rid", aid)
                .put("type", 2)
                .put("add_media_ids", fid)
                .put("del_media_ids", "")
                .put("csrf", SharedPreferencesUtil.getString(SharedPreferencesUtil.csrf, ""))
                .toString();

        JSONObject result = new JSONObject(Objects.requireNonNull(NetWorkUtil.post(url, per, videoHeaders(aid)).body()).string());
        Log.e("debug-添加收藏", result.toString());
        return result.getInt("code");
    }

    public static ApiResult getVideoStats(VideoInfo videoInfo) {
        ApiResult apiResult = new ApiResult();
        if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) == 0) return apiResult;
        try {
            String url = "https://api.bilibili.com/x/web-interface/archive/relation?aid=" + videoInfo.aid;
            JSONObject result = NetWorkUtil.getJson(url);
            apiResult.fromJson(result);
            JSONObject data = result.optJSONObject("data");
            if (data == null) return apiResult;
            videoInfo.stats.followed = data.optBoolean("attention");
            videoInfo.stats.liked = data.optBoolean("like");
            videoInfo.stats.disliked = data.optBoolean("dislik");
            videoInfo.stats.favoured = data.optBoolean("favorite");
            videoInfo.stats.coined = data.optInt("coin");
        } catch (Exception e) {
            MsgUtil.err(apiResult.message, e);
        }
        return apiResult;
    }

}

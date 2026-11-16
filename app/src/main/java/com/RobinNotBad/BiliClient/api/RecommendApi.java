package com.RobinNotBad.BiliClient.api;

import android.text.TextUtils;
import android.util.Log;

import com.RobinNotBad.BiliClient.model.VideoCard;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.StringUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;


//推荐API 自己写的
//#如果想要增/删/改内容，可以直接把url复制到浏览器里，把得到的一大串东西交给json解析软件就能缕清结构了，然后就是拆拆拆
//2023-07-12
//2023-12-09

public class RecommendApi {
    private static final long UNIQ_ID = (long) (new Random().nextDouble() * (1500000000000L - 1300000000000L));
    private static int freshIdx = 1;

    public static void getRecommend(List<VideoCard> videoCardList) throws IOException, JSONException {
        String url = ("https://api.bilibili.com/x/web-interface/wbi/index/top/feed/rcmd");
        url += new NetWorkUtil.FormData().setUrlParam(true)
                .put("version", 1)
                .put("web_location", 1430650)
                .put("feed_version", "V8")
                .put("homepage_ver", 1)
                .put("ps", 20)
                .put("fresh_type", 4)
                .put("fresh_idx", freshIdx)
                .put("brush", freshIdx)
                .put("uniq_id", UNIQ_ID)
                .put("screen", "1100-2056");
        freshIdx++;

        JSONObject result = NetWorkUtil.getJson(ConfInfoApi.signWBI(url));  //得到一整个json

        if (result.optInt("code", -1) != 0 || result.isNull("data")) return;

        JSONObject data = result.getJSONObject("data");  //推荐列表中的data项又是一个json，把它提出来
        JSONArray item = data.optJSONArray("item");  //data里面的items是视频卡片列表，把它提出来
        if (item == null) return;

        for (int i = 0; i < item.length(); i++) {    //遍历所有的视频卡片
            JSONObject card = item.getJSONObject(i);

            // 过滤广告、直播等非视频稿件
            String goto_ = card.optString("goto", "");
            if (!goto_.isEmpty() && !goto_.equals("av")) {
                Log.d("BiliClient", "RecommendApi getRecommend: skip goto=" + goto_);
                continue;
            }

            String bvid = card.optString("bvid", "");    //bv号
            if (TextUtils.isEmpty(bvid)) {
                Log.d("BiliClient", "RecommendApi getRecommend: isAd");
                continue;
            }
            if (card.isNull("owner") || card.isNull("stat")) continue;

            String cover = card.optString("pic", "");    //封面图片
            String title = card.optString("title", "");    //标题
            String upName = card.getJSONObject("owner").optString("name", "");  //up主名字
            String view = StringUtil.toWan(card.getJSONObject("stat").optInt("view", 0)) + "观看";    //播放量
            videoCardList.add(new VideoCard(title, upName, view, cover, 0, bvid));
        }
    }

    public static ArrayList<VideoCard> getRelated(long aid) throws JSONException, IOException {
        return getRelatedByUrl("https://api.bilibili.com/x/web-interface/archive/related?aid=" + aid);
    }

    public static ArrayList<VideoCard> getRelated(String bvid) throws JSONException, IOException {
        return getRelatedByUrl("https://api.bilibili.com/x/web-interface/archive/related?bvid=" + bvid);
    }

    private static ArrayList<VideoCard> getRelatedByUrl(String url) throws JSONException, IOException {
        JSONObject result = NetWorkUtil.getJson(url);  //得到一整个json

        ArrayList<VideoCard> videoList = new ArrayList<>();
        if (result.has("data") && !result.isNull("data")) {
            JSONArray data = result.getJSONArray("data");
            for (int i = 0; i < data.length(); i++) {
                JSONObject card = data.getJSONObject(i);
                VideoCard videoCard = new VideoCard();
                videoCard.aid = card.optLong("aid", 0);
                videoCard.view = StringUtil.toWan(card.getJSONObject("stat").optLong("view", 0)) + "观看";
                videoCard.cover = card.optString("pic", "");
                videoCard.title = card.optString("title", "");
                videoCard.upName = card.getJSONObject("owner").optString("name", "");
                videoCard.bvid = card.optString("bvid", "");
                videoList.add(videoCard);
            }

        }

        return videoList;
    }

    public static void getPopular(List<VideoCard> videoCardList, int page) throws JSONException, IOException {
        //热门接口在携带Cookie时返回的数据的排行是个性化的

        String url = "https://api.bilibili.com/x/web-interface/popular?pn=" + page + "&ps=10";

        JSONObject result = NetWorkUtil.getJson(url);  //得到一整个json

        if (result.has("data") && !result.isNull("data")) {
            if (result.getJSONObject("data").has("list")) {
                JSONArray list = result.getJSONObject("data").getJSONArray("list");
                for (int i = 0; i < list.length(); i++) {
                    JSONObject card = list.getJSONObject(i);
                    VideoCard videoCard = new VideoCard();
                    videoCard.aid = card.getLong("aid");
                    videoCard.cover = card.getString("pic");
                    videoCard.title = card.getString("title");
                    videoCard.upName = card.getJSONObject("owner").getString("name");
                    videoCard.view = StringUtil.toWan(card.getJSONObject("stat").getLong("view")) + "观看";
                    videoCardList.add(videoCard);
                }
            }
        }
    }

    public static void getPrecious(List<VideoCard> videoCardList, int page) throws JSONException, IOException {
        //热门接口在携带Cookie时返回的数据的排行是个性化的

        String url = "https://api.bilibili.com/x/web-interface/popular/precious?page=" + page + "&page_size=10";

        JSONObject result = NetWorkUtil.getJson(url);  //得到一整个json

        if (result.has("data") && !result.isNull("data")) {
            if (result.getJSONObject("data").has("list")) {
                JSONArray list = result.getJSONObject("data").getJSONArray("list");
                for (int i = 0; i < list.length(); i++) {
                    JSONObject card = list.getJSONObject(i);
                    VideoCard videoCard = new VideoCard();
                    videoCard.aid = card.getLong("aid");
                    videoCard.cover = card.getString("pic");
                    videoCard.title = card.getString("title");
                    videoCard.upName = card.getJSONObject("owner").getString("name");
                    videoCard.view = StringUtil.toWan(card.getJSONObject("stat").getLong("view")) + "观看";
                    videoCardList.add(videoCard);
                }
            }
        }
    }
}

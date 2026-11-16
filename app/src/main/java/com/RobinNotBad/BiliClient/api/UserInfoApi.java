package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.model.ArticleCard;
import com.RobinNotBad.BiliClient.model.LiveRoom;
import com.RobinNotBad.BiliClient.model.UserInfo;
import com.RobinNotBad.BiliClient.model.VideoCard;
import com.RobinNotBad.BiliClient.util.DmImgParamUtil;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.StringUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Objects;

//用户信息API

public class UserInfoApi {

    //APP端接口所需的统计参数
    private static final String STATISTICS_APP = "{\"appId\":1,\"platform\":3,\"version\":\"8.43.0\",\"abtest\":\"\"}";

    public static UserInfo getUserInfo(long mid) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/web-interface/card?mid=" + mid;
        JSONObject all = NetWorkUtil.getJson(url);
        if (all.has("data") && !all.isNull("data")) {
            JSONObject notice_all = NetWorkUtil.getJson("https://api.bilibili.com/x/space/notice?mid=" + mid);
            String notice;
            if (notice_all.has("data") && !notice_all.isNull("data"))
                notice = notice_all.getString("data");
            else notice = "";
            JSONObject data = all.getJSONObject("data");
            boolean followed = data.getBoolean("following");
            int fans = data.getInt("follower");

            JSONObject card = data.getJSONObject("card");
            String name = card.getString("name");
            String avatar = card.getString("face");
            String sign = card.getString("sign");
            JSONObject levelInfo = card.getJSONObject("level_info");
            int level = levelInfo.getInt("current_level");
            int attention = card.getInt("attention");

            JSONObject official_data = card.getJSONObject("Official");
            int official = official_data.getInt("role");
            String officialDesc = official_data.getString("title");

            String sys_notice = "";
            LiveRoom liveroom = null;
            boolean is_follow_display = false;
            try {
                JSONObject spaceInfo = getUserSpaceInfo(mid);
                if (spaceInfo != null) {
                    if (!spaceInfo.isNull("sys_notice")) {
                        sys_notice = spaceInfo.getJSONObject("sys_notice").optString("content");
                        if (sys_notice == null) sys_notice = "";
                        else sys_notice = sys_notice.replace("请点此查看纪念账号相关说明", "");
                    }
                    if (!spaceInfo.isNull("live_room")) {
                        JSONObject live_room = spaceInfo.getJSONObject("live_room");
                        if (live_room.getInt("roomStatus") == 1 && live_room.getInt("liveStatus") == 1) {
                            liveroom = new LiveRoom();
                            liveroom.title = "直播中：" + live_room.getString("title");
                            liveroom.user_cover = live_room.getString("cover");
                            liveroom.roomid = live_room.getLong("roomid");
                        }
                    }
                    if (!spaceInfo.isNull("contract")) {
                        JSONObject contract = spaceInfo.getJSONObject("contract");
                        is_follow_display = contract.optBoolean("is_follow_display", false);
                    }
                }
            } catch (Exception ignore) {
            }

            JSONObject vip = card.getJSONObject("vip");
            if (vip.getInt("status") == 1) {
                UserInfo result = new UserInfo(mid, name, avatar, sign, fans, attention, level, followed, notice, official, officialDesc, vip.getInt("role"), sys_notice, liveroom, card.getInt("is_senior_member"));
                result.vip_nickname_color = vip.optString("nickname_color", "");
                result.is_follow_display = is_follow_display;
                return result;
            } else {
                UserInfo result = new UserInfo(mid, name, avatar, sign, fans, attention, level, followed, notice, official, officialDesc, sys_notice, liveroom, card.getInt("is_senior_member"));
                result.is_follow_display = is_follow_display;
                return result;
            }
        } else return null;
    }

    public static JSONObject getUserSpaceInfo(long mid) throws JSONException, IOException {
        String url = "https://api.bilibili.com/x/space/wbi/acc/info?";
        url += "mid=" + mid;
        JSONObject all = NetWorkUtil.getJson(ConfInfoApi.signWBI(DmImgParamUtil.getDmImgParamsUrl(url)));
        if (all.has("data") && !all.isNull("data")) {
            return all.getJSONObject("data");
        }
        return null;
    }

    public static UserInfo getCurrentUserInfo() throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/space/myinfo";
        JSONObject all = NetWorkUtil.getJson(url);
        if (all.has("data") && !all.isNull("data")) {
            JSONObject data = all.getJSONObject("data");
            long mid = data.getLong("mid");
            String name = data.getString("name");
            String avatar = data.getString("face");
            String sign = data.getString("sign");
            int fans = data.getInt("follower");
            int level = data.getInt("level");

            JSONObject official_data = data.getJSONObject("official");
            int official = official_data.getInt("role");
            String officialDesc = official_data.getString("desc");

            JSONObject level_exp = data.getJSONObject("level_exp");
            long current_exp = level_exp.getLong("current_exp");
            long next_exp = level_exp.getLong("next_exp");

            return new UserInfo(mid, name, avatar, sign, fans, 0, level, false, "", official, officialDesc, current_exp, next_exp, data.getInt("is_senior_member"));
        } else return new UserInfo(0, "加载失败", "", "", 0, 0, 0, false, "", 0, "", 0);
    }

    public static int getCurrentUserCoin() {
        try {
            String url = "https://account.bilibili.com/site/getCoin";
            JSONObject all = NetWorkUtil.getJson(url);
            if (all.has("data") && !all.isNull("data")) {
                JSONObject data = all.getJSONObject("data");
                return data.has("money") ? data.getInt("money") : 0;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return 0;
    }


    public static int getUserVideos(long mid, int page, String searchKeyword, List<VideoCard> videoList) throws IOException, JSONException {
        //使用 web WBI 接口：app 端 /x/v2/space/archive/cursor 已失效（固定返回 -400），
        //web 端配合 WBI 签名与 dm_img 参数在未登录下可正常返回他人稿件列表。
        String url = "https://api.bilibili.com/x/space/wbi/arc/search?";
        url += "mid=" + mid
                + "&ps=30&tid=0&pn=" + page
                + "&keyword=" + URLEncoder.encode(searchKeyword == null ? "" : searchKeyword, "UTF-8")
                + "&order=pubdate"
                + "&platform=web"
                + "&web_location=333.1387"
                + "&order_avoided=true";
        url = ConfInfoApi.signWBI(DmImgParamUtil.getDmImgParamsUrl(url));
        JSONObject all = NetWorkUtil.getJson(url,
                NetWorkUtil.webHeadersWithRefererAndOrigin("https://space.bilibili.com/" + mid, "https://space.bilibili.com"));
        if (all.has("data") && !all.isNull("data")) {
            JSONObject data = all.getJSONObject("data");
            JSONObject listObj = data.optJSONObject("list");
            if (listObj == null) return -1;
            JSONArray list = listObj.optJSONArray("vlist");
            if (list == null) return -1;
            if (list.length() == 0) return 1;
            for (int i = 0; i < list.length(); i++) {
                JSONObject card = list.getJSONObject(i);
                String cover = card.optString("pic");
                long play = card.optLong("play");
                String playStr = StringUtil.toWan(play) + "观看";
                long aid = card.optLong("aid");
                String bvid = card.optString("bvid");
                String upName = card.optString("author");
                String title = card.optString("title");

                videoList.add(new VideoCard(title, upName, playStr, cover, aid, bvid));
            }
            return 0;
        } else return -1;
    }


    public static int getUserArticles(long mid, int page, List<ArticleCard> articleList) throws IOException, JSONException {
        //使用APP端接口，避免未登录时被风控拦截
        String url = "https://app.bilibili.com/x/v2/space/article?";
        url += "build=8430300&channel=master&version=8.43.0&c_locale=zh_CN&mobi_app=android&platform=android"
                + "&pn=" + page + "&ps=10&s_locale=zh_CN"
                + "&statistics=" + URLEncoder.encode(STATISTICS_APP, "UTF-8")
                + "&vmid=" + mid;
        JSONObject all = NetWorkUtil.getJson(url, NetWorkUtil.appHeaders);
        if (all.has("data") && !all.isNull("data")) {
            JSONObject data = all.getJSONObject("data");
            JSONArray list = data.optJSONArray("item");
            if (list == null) return -1;
            if (list.length() == 0) return 1;
            for (int i = 0; i < list.length(); i++) {
                JSONObject card = list.getJSONObject(i);

                ArticleCard articleCard = new ArticleCard();
                articleCard.id = getArticleId(card);
                articleCard.title = card.optString("title");
                JSONObject stats = card.optJSONObject("stats");
                articleCard.view = stats != null ? StringUtil.toWan(stats.optInt("view")) + "阅读" : "";
                JSONArray images = card.optJSONArray("origin_image_urls");
                articleCard.cover = (images != null && images.length() > 0) ? images.optString(0) : "";
                JSONObject author = card.optJSONObject("author");
                articleCard.upName = author != null ? author.optString("name") : "";
                articleList.add(articleCard);
            }
            return 0;
        } else return -1;
    }

    //APP端专栏列表只返回uri（bilibili://article/xxxxx），需要从中取出专栏id
    private static long getArticleId(JSONObject card) {
        long id = card.optLong("id");
        if (id != 0) return id;
        String uri = card.optString("uri");
        int index = uri.indexOf("article/");
        if (index == -1) return 0;
        String rest = uri.substring(index + "article/".length());
        int end = 0;
        while (end < rest.length() && Character.isDigit(rest.charAt(end))) end++;
        if (end == 0) return 0;
        try {
            return Long.parseLong(rest.substring(0, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static int followUser(long mid, boolean isFollow) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/relation/modify?";
        String arg = "fid=" + mid + "&csrf=" + NetWorkUtil.getInfoFromCookie("bili_jct", SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""));
        if (isFollow) arg += "&act=1"; //关注
        else arg += "&act=2"; //取消关注
        JSONObject all = new JSONObject(Objects.requireNonNull(NetWorkUtil.post(url, arg, NetWorkUtil.webHeaders).body()).string());
        return all.getInt("code");
    }

    public static void exitLogin() {
        try {
            String url = "https://passport.bilibili.com/login/exit/v2";
            NetWorkUtil.get(url, NetWorkUtil.webHeaders);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static int addContract(long upMid) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/v1/contract/add_contract";
        String csrf = NetWorkUtil.getInfoFromCookie("bili_jct", SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""));
        String arg = "aid=&up_mid=" + upMid + "&source=4&scene=105&platform=web&mobi_app=pc&csrf=" + csrf;
        JSONObject all = new JSONObject(Objects.requireNonNull(NetWorkUtil.post(url, arg, NetWorkUtil.webHeaders).body()).string());
        return all.getInt("code");
    }

    public static JSONObject getMedalWall(long targetId) throws IOException, JSONException {
        String url = "https://api.live.bilibili.com/xlive/web-ucenter/user/MedalWall?target_id=" + targetId;
        JSONObject all = NetWorkUtil.getJson(url, NetWorkUtil.webHeaders);
        if (all.has("data") && !all.isNull("data")) {
            return all.getJSONObject("data");
        }
        return null;
    }
}

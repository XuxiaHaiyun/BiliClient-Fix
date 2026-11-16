package com.RobinNotBad.BiliClient.api;

import android.util.Log;
import android.util.Pair;

import com.RobinNotBad.BiliClient.model.Collection;
import com.RobinNotBad.BiliClient.model.FavoriteFolder;
import com.RobinNotBad.BiliClient.model.Opus;
import com.RobinNotBad.BiliClient.model.VideoCard;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.StringUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

//收藏API

public class FavoriteApi {
    // TODO 合集收藏

    public static ArrayList<FavoriteFolder> getFavoriteFolders(long mid) throws IOException, JSONException {
        // 旧接口 space.bilibili.com/ajax/fav/getBoxList 已下线，改用 v3 接口
        String url = "https://api.bilibili.com/x/v3/fav/folder/created/list-all"
                + new NetWorkUtil.FormData()
                .setUrlParam(true)
                .put("up_mid", mid)
                .put("type", 2);
        JSONObject result = NetWorkUtil.getJson(url);
        ArrayList<FavoriteFolder> folderList = new ArrayList<>();
        JSONObject data = result.optJSONObject("data");
        if (data == null) return folderList;
        JSONArray list = data.optJSONArray("list");
        if (list == null) return folderList;

        for (int i = 0; i < list.length(); i++) {
            JSONObject folder = list.getJSONObject(i);

            FavoriteFolder favoriteFolder = new FavoriteFolder();

            favoriteFolder.id = folder.optLong("id", folder.optLong("fid", 0));
            favoriteFolder.name = folder.optString("title", "");
            favoriteFolder.cover = folder.optString("cover", "");
            favoriteFolder.videoCount = folder.optInt("media_count", 0);
            favoriteFolder.maxCount = folder.optInt("max_count", 1000);

            folderList.add(favoriteFolder);
        }
        return folderList;
    }

    /**
     * 获取收藏的合集
     *
     * @param mid            目标用户
     * @param page           页数
     * @param collectionList collection对象List
     * @return 返回码与has_more
     */
    public static Pair<Integer, Boolean> getFavoritedCollections(long mid, int page, List<Collection> collectionList) throws JSONException, IOException {
        String url = "https://api.bilibili.com/x/v3/fav/folder/collected/list" + new NetWorkUtil.FormData()
                .setUrlParam(true)
                .put("platform", "web")
                .put("up_mid", mid)
                .put("pn", page)
                .put("ps", 10);
        JSONObject result = NetWorkUtil.getJson(url);
        JSONObject data = result.optJSONObject("data");
        boolean has_more = false;
        if (data != null) {
            has_more = data.optBoolean("has_more", false);
            JSONArray list = data.optJSONArray("list");
            if (list != null) {
                for (int i = 0; i < list.length(); i++) {
                    JSONObject item = list.getJSONObject(i);
                    Collection collection = new Collection();
                    collection.id = item.optInt("id", -1);
                    collection.mid = item.optLong("mid", -1);
                    collection.title = item.optString("title");
                    collection.cover = item.optString("cover");
                    collection.intro = item.optString("intro");
                    collection.view = StringUtil.toWan(item.optInt("view_count", -1));
                    collectionList.add(collection);
                }
            }
        }
        return new Pair<>(result.optInt("code", -1), has_more);
    }

    public static int getFolderVideos(long mid, long fid, int page, ArrayList<VideoCard> videoList) throws IOException, JSONException {
        // 旧接口 /x/space/fav/arc 已下线，改用 v3 收藏夹资源列表
        String url = "https://api.bilibili.com/x/v3/fav/resource/list" + new NetWorkUtil.FormData()
                .setUrlParam(true)
                .put("media_id", fid)
                .put("pn", page)
                .put("ps", 30)
                .put("keyword", "")
                .put("order", "mtime")
                .put("type", 0)
                .put("tid", 0)
                .put("platform", "web");
        JSONObject result = NetWorkUtil.getJson(url);
        JSONObject data = result.optJSONObject("data");
        if (data == null) return -1;
        JSONArray medias = data.optJSONArray("medias");
        if (medias == null) return -1;
        if (medias.length() == 0) return 1;

        for (int i = 0; i < medias.length(); i++) {
            JSONObject media = medias.getJSONObject(i);
            String title = media.optString("title", "");
            if (title.isEmpty()) continue;  // 失效稿件

            String cover = media.optString("cover", "");
            long aid = media.optLong("id", 0);
            String bvid = media.optString("bvid", "");

            String upName = "";
            JSONObject owner = media.optJSONObject("upper");
            if (owner != null) upName = owner.optString("name", "");

            int play = 0;
            JSONObject cntInfo = media.optJSONObject("cnt_info");
            if (cntInfo != null) play = cntInfo.optInt("play", 0);

            String view = StringUtil.toWan(play) + "观看";
            videoList.add(new VideoCard(title, upName, view, cover, aid, bvid));
        }
        return 0;
    }

    public static boolean getFavouriteOpus(ArrayList<Opus> list, int page) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/polymer/web-dynamic/v1/opus/favlist?page_size=10&page=" + page;
        JSONObject result = NetWorkUtil.getJson(url);
        Log.e("OpusFav", result.toString());
        boolean hasMore = result.getJSONObject("data").getBoolean("has_more");
        if (!hasMore) Log.e("图文", "没有更多啦");
        JSONArray items = result.getJSONObject("data").getJSONArray("items");

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            Opus opus = new Opus();
            opus.content = item.optString("content");
            opus.cover = item.optString("cover");
            opus.title = item.optString("title");
            if (opus.title.isEmpty()) opus.title = opus.content;
            opus.id = Long.parseLong(item.getString("opus_id"));
            opus.pubTime = item.optString("time_text");
            list.add(opus);
        }

        return hasMore;
    }

    public static void getFavoriteState(long aid, ArrayList<String> folderList, ArrayList<Long> fidList, ArrayList<Boolean> stateList) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/v3/fav/folder/created/list-all?type=2&jsonp=jsonp&rid=" + aid + "&up_mid=" + SharedPreferencesUtil.getLong("mid", 0);
        JSONObject result = NetWorkUtil.getJson(url);
        JSONObject data = result.getJSONObject("data");

        if (data.has("list") && !data.isNull("list")) {
            JSONArray list = data.getJSONArray("list");
            for (int i = 0; i < list.length(); i++) {
                JSONObject folder = list.getJSONObject(i);
                folderList.add(folder.getString("title"));
                fidList.add(folder.getLong("fid"));
                stateList.add(folder.getInt("fav_state") == 1);
            }
        }
    }

    public static int addFavorite(long aid, long fid) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/v3/fav/resource/deal";
        String per = new NetWorkUtil.FormData()
                .put("rid", aid)
                .put("type", 2)
                .put("add_media_ids", fid)
                .put("del_media_ids", "")
                .put("csrf", SharedPreferencesUtil.getString(SharedPreferencesUtil.csrf, ""))
                .toString();

        JSONObject result = new JSONObject(Objects.requireNonNull(NetWorkUtil.post(url, per, NetWorkUtil.webHeaders).body()).string());
        Log.e("debug-添加收藏", result.toString());
        return result.getInt("code");
    }


    public static int deleteFavorite(long aid, long fid) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/v3/fav/resource/deal";
        String per = new NetWorkUtil.FormData()
                .put("rid", aid)
                .put("type", 2)
                .put("add_media_ids", "")
                .put("del_media_ids", fid)
                .put("csrf", SharedPreferencesUtil.getString(SharedPreferencesUtil.csrf, ""))
                .toString();

        JSONObject result = new JSONObject(Objects.requireNonNull(NetWorkUtil.post(url, per, NetWorkUtil.webHeaders).body()).string());
        Log.e("debug-删除收藏", result.toString());
        return result.getInt("code");
    }

}

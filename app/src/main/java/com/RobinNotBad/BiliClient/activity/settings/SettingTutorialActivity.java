package com.RobinNotBad.BiliClient.activity.settings;

import android.content.Intent;
import android.os.Bundle;

import com.RobinNotBad.BiliClient.activity.TutorialActivity;
import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity;
import com.RobinNotBad.BiliClient.adapter.TutorialAdapter;
import com.RobinNotBad.BiliClient.helper.TutorialHelper;
import com.RobinNotBad.BiliClient.model.SettingSection;
import com.RobinNotBad.BiliClient.model.Tutorial;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;

import java.util.ArrayList;
import java.util.List;

//操作教程，按原页面名称分类展示所有教程
public class SettingTutorialActivity extends RefreshListActivity {

    //页面名称, 教程数组名, 教程进度tag
    private static final String[][] TUTORIAL_PAGES = {
            {"推荐页", "tutorial_recommend", "recommend"},
            {"视频详情页", "tutorial_video", "video"},
            {"用户空间", "tutorial_space", "space"},
            {"搜索页", "tutorial_search", "search"},
            {"消息页", "tutorial_message", "message"},
            {"动态页", "tutorial_dynamic", "dynamic"},
            {"动态详情页", "tutorial_dynamic_info", "dynamic_info"},
            {"专栏详情页", "tutorial_article", "article"},
            {"更新安装", "tutorial_update_install", "update_install"},
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setPageName("操作教程");

        CenterThreadPool.run(() -> {
            List<SettingSection> sectionList = new ArrayList<>();
            for (String[] page : TUTORIAL_PAGES) {
                int arrayId = getResources().getIdentifier(page[1], "array", getPackageName());
                if (arrayId == 0) continue;
                String[] tutorials = getResources().getStringArray(arrayId);
                if (tutorials.length == 0) continue;

                sectionList.add(new SettingSection("title", page[0], "", "", ""));
                int total = tutorials.length;
                for (int i = 0; i < total; i++) {
                    int xmlId = getResources().getIdentifier(getPackageName() + ":" + tutorials[i], null, null);
                    if (xmlId == 0) continue;
                    Tutorial tutorial = TutorialHelper.loadTutorial(getResources().getXml(xmlId));
                    String name = (tutorial != null && tutorial.name != null) ? tutorial.name : page[0];
                    sectionList.add(new SettingSection("goto", name, page[2], "", "", new int[]{xmlId, total - i}));
                }
            }
            setRefreshing(false);
            setAdapter(new TutorialAdapter(this, sectionList, this::openTutorial));
        });
    }

    private void openTutorial(SettingSection section) {
        int[] extra = (int[]) section.extra;
        Intent intent = new Intent(this, TutorialActivity.class);
        intent.putExtra("xml_id", extra[0]);
        intent.putExtra("tag", section.id);
        intent.putExtra("version", extra[1]);
        startActivity(intent);
    }
}

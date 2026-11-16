package com.RobinNotBad.BiliClient.activity;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;

import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.util.LinkUrlUtil;
import com.RobinNotBad.BiliClient.util.MsgUtil;

public class GetIntentActivity extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String type = intent.getStringExtra("type");

        if (type != null) switch (intent.getStringExtra("type")) {
            case "video_av":
                BiliTerminal.jumpToVideo(this, intent.getLongExtra("content", 0));
                break;
            case "video_bv":
                BiliTerminal.jumpToVideo(this, intent.getStringExtra("content"));
                break;
            case "article":
                BiliTerminal.jumpToArticle(this, intent.getLongExtra("content", 0));
                break;
            case "user":
                BiliTerminal.jumpToUser(this, intent.getLongExtra("content", 0));
                break;
            default:
                MsgUtil.showMsgLong("不支持打开：" + type);
                break;
        }

        Uri uri = intent.getData();
        if (uri != null) {
            String scheme = uri.getScheme();
            String host = uri.getHost();
            Log.e("debug-host", String.valueOf(host));

            if ("bilibili".equalsIgnoreCase(scheme)) {
                // bilibili://video/123 、bilibili://article/123
                switch (host == null ? "" : host) {
                    case "video":
                        BiliTerminal.jumpToVideo(this, Long.parseLong(uri.getLastPathSegment()));
                        break;
                    case "article":
                        BiliTerminal.jumpToArticle(this, Long.parseLong(uri.getLastPathSegment()));
                        break;
                    default:
                        MsgUtil.showMsgLong("不支持打开：" + host);
                        break;
                }
            } else if (host != null && (host.equals("b23.tv") || host.endsWith("bilibili.com"))) {
                // http(s) 分享链接：交给统一解析，支持 BV/av/cv/uid 与 b23.tv 短链跳转
                LinkUrlUtil.handleWebURL(this, uri.toString());
            } else {
                MsgUtil.showMsgLong("不支持打开：" + host);
            }
        }

        finish();
    }
}

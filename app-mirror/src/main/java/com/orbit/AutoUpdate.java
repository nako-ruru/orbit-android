package com.orbit;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import com.azhon.appupdate.manager.DownloadManager;
import com.connect_screen.mirror.BuildConfig;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class AutoUpdate {

    // 替换为你的代理地址
    private static final String proxyUrl = BuildConfig.UPDATE_URL;

    /**
     * 检查更新静态方法
     * @param activity 必须传入 Activity，因为 DownloadManager 需要 Activity 的 Context 来弹窗
     */
    public static void checkUpdate(final Activity activity) {
        final ObjectMapper mapper = new ObjectMapper();
        final OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder().url(proxyUrl).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    return;
                }

                final GitHubRelease release = mapper.readValue(response.body().byteStream(), GitHubRelease.class);

                // 1. 提取 versionCode [VC:xx]
                int vc = 0;
                if (release.body != null) {
                    Matcher m = Pattern.compile("\\[VC:(\\d+)\\]").matcher(release.body);
                    if (m.find()) vc = Integer.parseInt(m.group(1));
                }

                // 2. 寻找 APK 信息
                String url = "";
                long size = 0;
                if (release.assets != null) {
                    for (GitHubRelease.Asset asset : release.assets) {
                        if (asset.name != null && asset.name.toLowerCase().endsWith(".apk")) {
                            url = asset.browserDownloadUrl;
                            size = asset.size;
                            break;
                        }
                    }
                }

                // 3. 切换回主线程触发弹窗
                final int finalVC = vc;
                final String finalUrl = url;
                final String sizeText = String.format("%.2fMB", size / 1024.0 / 1024.0);

                new Handler(Looper.getMainLooper()).post(() -> {
                    // 检查 Activity 是否还在运行，防止崩溃
                    if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                        return;
                    }

                    new DownloadManager.Builder(activity)
                            .apkUrl(finalUrl)
                            .apkVersionCode(finalVC)
                            .apkVersionName(release.tagName)
                            .apkSize(sizeText)
                            .apkDescription(release.body)
                            .build()
                            .download();
                });
            }
        });
    }

    // 忽略 JSON 中多余的字段，防止报错
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GitHubRelease {
        @JsonProperty("tag_name")
        public String tagName; // 名义版本号，如 "v1.6.3"

        public String body;    // 更新日志，用于提取 [VC:xx]

        public List<Asset> assets;

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Asset {
            public String name;
            public long size;  // 文件大小（字节）

            @JsonProperty("browser_download_url")
            public String browserDownloadUrl; // 下载地址
        }
    }

}

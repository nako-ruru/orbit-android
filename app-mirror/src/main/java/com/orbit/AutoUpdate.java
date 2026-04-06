package com.orbit;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import com.azhon.appupdate.manager.DownloadManager;
import com.connect_screen.mirror.BuildConfig;
import com.connect_screen.mirror.R;
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

                // 1. 解析 GitHub API 返回的 JSON
                final GitHubRelease release = mapper.readValue(response.body().byteStream(), GitHubRelease.class);
                if (release == null || release.body == null) {
                    return;
                }

                // 2. 使用 Parser 解析 Body 中的元数据 (这是我们设计的接力棒逻辑)
                final Metadata meta = parse(release.body);
                if (meta == null) {
                    return;
                }

                // 3. 核心版本比对逻辑
                int currentVC = BuildConfig.VERSION_CODE;

                // 如果线上版本不大于当前版本，直接退出
                if (meta.versionCode <= currentVC) {
                    return;
                }

                // 只有当“通知断点”大于当前版本时，才触发弹窗提醒 (实现静默更新)
                if (meta.lastNotifyVersionCode <= currentVC) {
                    return;
                }

                // 4. 确定是否强制更新
                final boolean isForce = meta.lastForceVersionCode > currentVC;

                // 5. 寻找有效的 APK Asset 对象
                GitHubRelease.Asset targetAsset = null;
                if (release.assets != null) {
                    for (GitHubRelease.Asset asset : release.assets) {
                        if (asset.name != null && asset.name.toLowerCase().endsWith(".apk")) {
                            targetAsset = asset;
                            break;
                        }
                    }
                }

                if (targetAsset == null) {
                    return;
                }


                // 6. 准备 UI 展示数据
                final String finalApkName = targetAsset.name;
                final String sizeText = String.format("%.2fMB", targetAsset.size / 1024.0 / 1024.0);

                String finalUrl;
                // 提取分组说明：
                // Group 1: 协议 + Authority (http://192.168.3.253:8811)
                // Group 2: Owner
                // Group 3: Repo
                String regex = "^(https?://[^/]+)/repos/([^/]+)/([^/]+)/.*";
                Pattern pattern = Pattern.compile(regex);
                Matcher matcher = pattern.matcher(BuildConfig.UPDATE_URL);
                if (matcher.find()) {
                    String baseHost = matcher.group(1); // 包含协议和主机端口
                    String owner = matcher.group(2);
                    String repo = matcher.group(3);
                    finalUrl = String.format("%s/%s/%s/releases/download/%s/%s",
                            baseHost,
                            owner,
                            repo,
                            release.tagName,
                            targetAsset.name);
                } else {
                    throw new RuntimeException();
                }
                // 7. 切换回主线程触发弹窗
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                        return;
                    }

                    new DownloadManager.Builder(activity)
                            .apkUrl(finalUrl)
                            .apkName(finalApkName) // 使用从 Asset 获取的真实文件名
                            .smallIcon(R.mipmap.ic_orbit)
                            .apkVersionCode(meta.versionCode)
                            .apkVersionName(release.tagName)
                            .apkSize(sizeText)
                            .apkDescription(release.body)
                            .forcedUpgrade(isForce)
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

    public static class Metadata {
        public int versionCode;
        public int lastNotifyVersionCode;
        public int lastForceVersionCode;

        @Override
        public String toString() {
            return "Metadata{" +
                    "versionCode=" + versionCode +
                    ", lastNotify=" + lastNotifyVersionCode +
                    ", lastForce=" + lastForceVersionCode +
                    '}';
        }
    }

    /**
     * 从 Release Body 字符串中提取元数据
     */
    public static Metadata parse(String body) {
        if (body == null || body.isEmpty()) return null;

        // 1. 锁定元数据块 (提取 START 到 END 之间的内容)
        String blockRegex = "RELEASE_METADATA_START[\\s\\S]*?RELEASE_METADATA_END";
        Matcher blockMatcher = Pattern.compile(blockRegex).matcher(body);

        if (blockMatcher.find()) {
            String metaBlock = blockMatcher.group();
            Metadata meta = new Metadata();

            // 2. 分别提取三个 Key 的 Value
            meta.versionCode = extractInt(metaBlock, "versionCode");
            meta.lastNotifyVersionCode = extractInt(metaBlock, "lastNotifyVersionCode");
            meta.lastForceVersionCode = extractInt(metaBlock, "lastForceVersionCode");

            return meta;
        }
        return null;
    }

    private static int extractInt(String block, String key) {
        // 匹配 key=数字
        String regex = key + "=(\\d+)";
        Matcher matcher = Pattern.compile(regex).matcher(block);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

}

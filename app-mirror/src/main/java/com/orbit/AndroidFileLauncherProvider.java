package com.orbit;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import androidx.core.content.FileProvider;

import java.io.File;

import aar.FileLauncherProvider;

public class AndroidFileLauncherProvider        implements FileLauncherProvider {

    private final Context context;

    public AndroidFileLauncherProvider(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public void open(String path) {
        File file = new File(path);

        Uri uri = FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                file
        );

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, getMime(path));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        context.startActivity(intent);
    }

    @Override
    public void reveal(String path) {
        File file = new File(path);

        Uri uri = FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                file.getParentFile()
        );

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "resource/folder");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        context.startActivity(intent);
    }

    public void openURL(String urlStr) {
        Uri uri = Uri.parse(urlStr);
        Intent intent = new Intent(Intent.ACTION_VIEW);

        // 根据后缀猜测 MIME 类型
        if (isMedia(urlStr)) {
            // 声明为视频流，系统会自动筛选支持网络流的播放器（MXPlayer, VLC等）
            intent.setDataAndType(uri, "video/*");
        } else {
            // 普通网页
            intent.setData(uri);
        }

        // 强制弹出系统选择器，不使用默认行为
        Intent chooser = Intent.createChooser(intent, "请选择应用打开链接");

        // 如果是在非 Activity 上下文（如 Service 或 Go 线程）启动，需要加这个 Flag
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        context.startActivity(chooser);
    }

    private boolean isMedia(String urlStr) {
        String lower = urlStr.toLowerCase();
        return lower.contains(".mp4") || lower.contains(".mkv") ||
                lower.contains(".m3u8") || lower.contains(".avi");
    }

    private String getMime(String path) {
        String ext = path.substring(path.lastIndexOf('.') + 1);
        return MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(ext);
    }
}
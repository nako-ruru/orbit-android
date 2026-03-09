package com.orbit;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.AssetManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.connect_screen.mirror.job.ExitAll;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import fi.iki.elonen.NanoHTTPD;

public class StreamerService extends Service {

    public StreamerService() {
        super();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC | ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);

        NanoHTTPD httpd = new NanoHTTPD(9723) {
            @Override
            public Response serve(IHTTPSession session) {
                Log.d("TestServer", "serve");
                // 获取 URL 路径，例如 /trigger
                String uri = session.getUri();
                // 获取参数，例如 ?cmd=open
                java.util.Map<String, String> params = session.getParms();

                if ("/trigger".equals(uri)) {
                    String cmd = params.get("cmd");

                    // --- 在这里执行你的测试逻辑 ---
                    ExitAll.execute(StreamerService.this, true);

                    return newFixedLengthResponse("Android 已接收指令: " + cmd);
                }

                return newFixedLengthResponse("Server 正在运行，但路径不匹配");
            }
        };
        try {
            httpd.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            Log.d("TestServer", "Server 已启动，监听端口 9723");
        } catch (IOException e) {
            e.printStackTrace();
        }

        new Thread(() -> {
            try {
                startWebServer();
            } catch (IOException |InterruptedException e) {
                throw new RuntimeException(e);
            }
        }).start();

        return START_STICKY;
    }

    private void startWebServer() throws IOException, InterruptedException {
        File writableDir = new File(getFilesDir(), "streamer");
        copyAssetsFolder(this, "streamer", writableDir);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification buildNotification() {
        String channelId = "streamer_service";
        String channelName = "Streamer Service";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, channelName,
                    NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }

        return new NotificationCompat.Builder(this, channelId)
                .setContentTitle("Streamer Service")
                .setContentText("Running streamer...")
                .setSmallIcon(android.R.drawable.ic_menu_upload)
                .build();
    }

    /**
     * 递归复制 Assets 文件夹到目标路径
     * @param context 上下文
     * @param assetPath Assets 内的文件夹路径 (例如 "my_data")
     * @param targetPath 目标物理路径 (例如 context.getFilesDir().getAbsolutePath() + "/my_data")
     */
    public static void copyAssetsFolder(Context context, String assetPath, File targetPath) throws IOException {
        AssetManager assetManager = context.getAssets();
        String[] assets = assetManager.list(assetPath);

        if (assets != null && assets.length > 0) {
            // 如果是文件夹，创建目标目录
            if (!targetPath.exists()) {
                targetPath.mkdirs();
            }

            // 递归子文件/文件夹
            for (String asset : assets) {
                String subAssetPath = assetPath.isEmpty() ? asset : assetPath + "/" + asset;
                File subDir = new File(targetPath,  asset);
                copyAssetsFolder(context, subAssetPath, subDir);
            }
        } else {
            // 如果是文件，直接复制
            copyFile(assetManager.open(assetPath), targetPath);
        }
    }

    private static void copyFile(InputStream in, File targetFile) throws IOException {
        try (OutputStream out = new FileOutputStream(targetFile)) {
            byte[] buffer = new byte[1024 * 8]; // 8KB 缓冲区
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } finally {
            if (in != null) in.close();
        }
    }
}


package com.orbit;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

public class StreamerService extends Service {

    private Process process;

    public StreamerService() {
        super();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1, buildNotification());

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
        String libraryPath = getApplicationInfo().nativeLibraryDir;

        copyAssetsFolder(this, "streamer", writableDir);
        ObjectMapper mapper = new ObjectMapper();
        File file = new File(writableDir, "server/config.json");
        // 读取为树结构
        JsonNode rootNode = mapper.readTree(file);
        if (rootNode instanceof ObjectNode objectNode) {
            objectNode.put("streamer_path", new File(libraryPath, "libstreamer.so").getAbsolutePath());
        }
        // 写回文件（格式化输出）
        mapper.writerWithDefaultPrettyPrinter().writeValue(file, rootNode);

        File exeFile = new File(libraryPath, "libweb-server.so"); // 注意文件名
        ProcessBuilder pb = new ProcessBuilder(exeFile.getAbsolutePath());
        pb.directory(writableDir);
        pb.redirectErrorStream(true); // 合并 stdout 和 stderr
        process = pb.start();

        // 读取输出
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
        );
        String line;
        while ((line = reader.readLine()) != null) {
            Log.d("RcloneService", line);
        }

        int exitCode = process.waitFor();
        Log.d("RcloneService", "rclone exited with " + exitCode);
    }

    @Override
    public void onDestroy() {
        if (process != null) {
            process.destroy();
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification buildNotification() {
        String channelId = "rclone_service";
        String channelName = "Rclone Service";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, channelName,
                    NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }

        return new NotificationCompat.Builder(this, channelId)
                .setContentTitle("Rclone Service")
                .setContentText("Running rclone...")
                .setSmallIcon(android.R.drawable.ic_menu_upload)
                .build();
    }

    public void copyFolder(File src, File dest) throws IOException {
        if (src.isDirectory()) {
            if (!dest.exists()) dest.mkdirs(); // 创建目标文件夹
            String[] children = src.list();
            for (String child : children) {
                copyFolder(new File(src, child), new File(dest, child));
            }
        } else {
            // 复制单个文件
            try (InputStream in = new FileInputStream(src);
                 OutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }
        }
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


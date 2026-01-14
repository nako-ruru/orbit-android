package com.orbit;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

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
                startRcloneRcd();
            } catch (IOException |InterruptedException e) {
                throw new RuntimeException(e);
            }
        }).start();

        return START_STICKY;
    }

    private void startRcloneRcd() throws IOException, InterruptedException {
        // 二进制路径
        String binaryPath = this.getApplicationInfo().nativeLibraryDir + "/libstreamer.so";
        // 确保可执行
        File file = new File(binaryPath);
        /*
        if(!file.exists()) {
            String src = "moonlight-web-aarch64-unknown-linux-gnu";
            AssetsUtils.copyAssetsFolder(this, src, new File(getFilesDir().getAbsolutePath() + "/moonlight-web-aarch64-unknown-linux-gnu"));
        }

        if (!file.setExecutable(true)) {
            Log.e("RcloneService", "Failed to set executable");
        }

         */
        if (file.exists() && file.canExecute()) {
            Log.d("ExecCheck", "Ready to execute");
        } else {
            Log.e("ExecCheck", "File missing or not executable");
        }

        // 构建进程
        ProcessBuilder pb = new ProcessBuilder(        binaryPath, "--help"  );

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
}


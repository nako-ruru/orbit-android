package com.orbit; // 请务必修改为你自己的包名

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;

import com.connect_screen.mirror.R;
import com.connect_screen.mirror.State;
import com.connect_screen.mirror.shizuku.ShizukuUtils;

public class KeepAliveService extends Service {
    private static final String CHANNEL_ID = "keep_alive_channel";
    private PowerManager.WakeLock wakeLock = null;
    @Override
    public void onCreate() {
        super.onCreate();

        // 1. 获取电源管理器
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);

        // 2. 创建 WakeLock
        // 标签 "Orbit:MyWakeLock" 方便你在调试时追踪是谁在耗电
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Orbit:MyWakeLock");

        // 3. 锁定！这时候即使关屏，CPU 也会为你转动
        // 建议加上超时或者在 onDestroy 中手动释放
        wakeLock.acquire();

        // 创建通知渠道（Android 8.0+ 必须）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "无人值守守护进程", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }

        // 构建通知
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("服务运行中")
                .setContentText("正在保持连接...")
                .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery)
                .build();

        // 【关键】启动为前台服务
        // 这一步能让你的 Go 协程在后台不被冷冻
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(
                    5,
                    createNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
            );
        } else {
            startForeground(5, createNotification());
        }

        OrbitApplication.test(this, "1");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Handler handler = new Handler();
        handler.post(() -> {
            if (ShizukuUtils.hasPermission() && State.userService == null) {
                State.log("try start shizuku user service");
                State.bindUserService();
            }
        });
        return START_STICKY; // 被杀后尝试自动拉起
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // 2. 停止服务自身
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        // 4. 服务销毁时一定要记得释放，否则用户手机会一直耗电！
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }
        super.onDestroy();
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("屏易连")
                .setContentText("Sunshine 服务正在运行")
                .setSmallIcon(R.mipmap.ic_orbit)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
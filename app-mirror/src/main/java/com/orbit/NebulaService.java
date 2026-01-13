package com.orbit;

import android.net.VpnService;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import android.os.ParcelFileDescriptor;

public class NebulaService extends VpnService {

    private static final String CHANNEL_ID = "vpn_service_channel";
    private static final int NOTIFICATION_ID = 1; // 必须是非 0 整数

    public static ParcelFileDescriptor vpnInterface;

    @Override
    public void onCreate() {
        super.onCreate();
        // 第一步：【必须】先创建并注册通知渠道
        String channelId = "VPN_CHANNEL";
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                    channelId,
                    "VPN Service Status",
                    android.app.NotificationManager.IMPORTANCE_LOW);

            android.app.NotificationManager manager = getSystemService(android.app.NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        // 第二步：构建通知（确保 ID 匹配）
        android.app.Notification notification = new android.app.Notification.Builder(this, channelId)
                .setContentTitle("Nebula VPN")
                .setContentText("VPN 正在运行中...")
                .setSmallIcon(android.R.drawable.ic_dialog_info) // 使用系统内置图标
                .setOngoing(true) // 前台服务通知建议设为正在进行
                .build();

        // 第三步：【必须】启动前台服务，且 ID 不能为 0
        try {
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                startForeground(1001, notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(1001, notification);
            }
        } catch (Exception e) {
            // 如果这里还崩，说明 AndroidManifest.xml 里的 foregroundServiceType 没写对
            android.util.Log.e("VPN", "启动前台服务失败: " + e.getMessage());
        }
    }

    private Notification createNotification() {
        Notification notification = new Notification.Builder(this, "VPN_CHANNEL")
                .setContentTitle("Nebula VPN")
                .setContentText("VPN 正在运行中...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build();
        return notification;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "VPN 服务状态",
                    NotificationManager.IMPORTANCE_LOW // 建议用 LOW，不会有烦人的提示音
            );
            channel.setDescription("用于显示 VPN 隧道运行状态的通知");

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public int onStartCommand(android.content.Intent intent, int flags, int startId) {
        try {
            Builder builder = new Builder()
                    .setSession("DemoVPN")
//                    .addAllowedApplication("com.orbit")
                    .addRoute("fdc8:d0db:a315:cb00::0", 64)
            ;
            for(String address: intent.getStringArrayExtra("FIXED_IPS")) {
                builder = builder.addAddress(address, 24);
            }
            vpnInterface = builder.establish();
            AndroidTunProvider.future.complete(new Object());
            return START_STICKY;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (vpnInterface != null) {
                vpnInterface.close();
            }
        } catch (Exception ignored) {}
    }
}
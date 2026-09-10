package com.orbit;

import android.net.VpnService;

import android.os.ParcelFileDescriptor;

import com.easytier.jni.EasyTierJNI;

public class PrivNetworkService extends VpnService {

    private ParcelFileDescriptor vpnInterface;

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

    @Override
    public int onStartCommand(android.content.Intent intent, int flags, int startId) {
        try {
            Builder builder = new Builder()
                    .setSession("default")
                    .addAllowedApplication("com.orbit")
                    .addAllowedApplication("com.android.shell");

            for (IpRange ipRange : PRIV_IP_PREFIXES) {
                builder.addRoute(ipRange.address, ipRange.prefixLength);
            }

            String[] fixedIps = intent.getStringArrayExtra("FIXED_IPS");
            if (fixedIps != null) {
                for (String ip : fixedIps) {
                    if (ip.contains(":")) {
                        builder.addAddress(ip, 128);
                    } else {
                        builder.addAddress(ip, 32);
                    }
                }
            }
            vpnInterface = builder.establish();
            int fd = vpnInterface.getFd();
            new Thread(() -> {
                int res = EasyTierJNI.setTunFd("easytier", fd);
                if (res == -1) {
                    throw new RuntimeException();
                }
            }).start();
            return START_STICKY;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
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

    private static final IpRange[] PRIV_IP_PREFIXES = new IpRange[] {
        new IpRange("10.0.0.0", 8),
        new IpRange("172.16.0.0", 12),
//        new IpRange("192.168.0.0", 16),
        new IpRange("100.64.0.0", 10),
        new IpRange("198.18.0.0", 15),
        new IpRange("fc00::", 7),
    };

    private static class IpRange {
        public final String address;
        public final int prefixLength;

        public IpRange(String address, int prefixLength) {
            this.address = address;
            this.prefixLength = prefixLength;
        }
    }
}
package com.connect_screen.mirror;

import static android.app.Activity.RESULT_OK;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.media.MediaCodecInfo;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.connect_screen.mirror.job.MirrorDisplayMonitor;
import com.connect_screen.mirror.job.MirrorDisplaylinkMonitor;
import com.connect_screen.mirror.job.SunshineServer;
import com.connect_screen.mirror.shizuku.PermissionManager;
import com.connect_screen.mirror.shizuku.ShizukuUtils;
import com.orbit.CertificateGenerator;

import org.bouncycastle.operator.OperatorCreationException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SunshineService extends Service {
    public static final String ACTION_USB_PERMISSION = "com.connect_screen.mirror.USB_PERMISSION";
    public static SunshineService instance;
    private static final String CHANNEL_ID = "SunshineServiceChannel";
    private static final int NOTIFICATION_ID = 2;
    private static final String TAG = "SunshineService";

    private final BroadcastReceiver usbPermissionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            android.util.Log.d("SunshineService", "received action: " + intent.getAction());
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                State.resumeJob();
            }
        }
    };

    private int currentTimeout;

    @Override
    public void onCreate() {
        Log.i("SunshineService", "onCreate: 0");
        super.onCreate();
        instance = this;
        createNotificationChannel();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        releaseWakeLock();
        try {
            unregisterReceiver(usbPermissionReceiver);
        } catch (Exception e) {
            // ignore
        }
        //MirrorDisplaylinkMonitor.release(this);
        State.unbindUserService();
    }

    public void releaseWakeLock() {
        if (currentTimeout > 0) {
            Settings.System.putInt(this.getContentResolver(),
                    Settings.System.SCREEN_OFF_TIMEOUT, currentTimeout);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("SunshineService", "onStartCommand 运行了，intent 是否为空: " + (intent == null));

        // 修改这里：增加第三个参数指定服务类型
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(
                    NOTIFICATION_ID,
                    createNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            );
        } else {
            startForeground(NOTIFICATION_ID, createNotification());
        }

        if (intent != null && intent.hasExtra("data")) {
            MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            Intent data = intent.getParcelableExtra("data");
            State.setMediaProjection(mediaProjectionManager.getMediaProjection(RESULT_OK, data));
            State.getMediaProjection().registerCallback(new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    super.onStop();
                    State.log("MediaProjection onStop 回调");
                }
            }, null);
            State.resumeJob();
        } else {
            State.log("SunshineService 收到错误的授权数据");
            State.resumeJob();
        }
        if (Pref.getPreventAutoLock()) {
            preventAutoLock();
        }
        // 在后台线程中启动 Sunshine 服务器
        String sunshineName = "屏易连-" + Build.MANUFACTURER + "-" + Build.MODEL;
        SunshineServer.setSunshineName(sunshineName);
        probeH265();

        new Thread(() -> {
            try {
                SunshineServer.setFileStatePath(SunshineService.this.getFilesDir().getAbsolutePath() + "/sunshine_state.json");
                writeCertAndKey(SunshineService.this);
                Log.i("SunshineService", "onStartCommand: 2");
                new Thread(() -> {
                    try {
                        SunshineServer.start();
                        Log.i("SunshineService", "onStartCommand: 3");
                    } catch (Throwable e) {
                        Log.e("SunshineService", "thread quit", e);
                    }
                }).start();
            } catch (Exception e) {
                Log.e("SunshineService", "初始化网络服务失败", e);
            }
        }).start();

        // 注册 USB 权限广播接收器
        IntentFilter permissionFilter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(usbPermissionReceiver, permissionFilter, null, null, Context.RECEIVER_EXPORTED);

        // 监听显示器变化
        DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
//        MirrorDisplayMonitor.init(displayManager);
//        MirrorDisplaylinkMonitor.init(this);
//        State.refreshMainActivity();
        Handler handler = new Handler();
        handler.postDelayed(() -> {
            if (ShizukuUtils.hasPermission() && State.userService == null) {
                State.log("try start shizuku user service");
                State.bindUserService();
                handler.postDelayed(() -> {
                    if (ShizukuUtils.hasPermission() && State.userService == null) {
                        State.log("shizuku user service 启动失败，请取消 shizuku 授权并再次授予。try start user service again");
                        State.unbindUserService();
                        State.bindUserService();
                    }
                }, 15 * 1000);
            }
        }, 2000);
        return START_NOT_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.i("SunshineService", "检测到划掉卡片，主动自杀");

        // 1. 停止前台状态（撤销通知）
        stopForeground(true);

        // 2. 停止服务自身
        stopSelf();

        super.onTaskRemoved(rootIntent);
    }


    private void preventAutoLock() {
        if (!ShizukuUtils.hasPermission()) {
            return;
        }
        if (PermissionManager.grant("android.permission.WRITE_SECURE_SETTINGS")) {
            // 读取当前的屏幕超时设置
            currentTimeout = Settings.System.getInt(this.getContentResolver(),
                    Settings.System.SCREEN_OFF_TIMEOUT, 0);
            Log.i("SunshineService", "当前屏幕超时设置: " + currentTimeout + "ms");
            if (currentTimeout >= 4 * 60 * 60 * 1000) {
                currentTimeout = 15 * 1000;
            }
            Settings.System.putInt(this.getContentResolver(),
                    Settings.System.SCREEN_OFF_TIMEOUT, 4 * 60 * 60 * 1000);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Sunshine Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("屏易连")
                .setContentText("Sunshine 服务正在运行")
                .setSmallIcon(R.mipmap.ic_orbit)
                .build();
    }

    private boolean probeH265() {
        try {
            // 检查设备是否支持 H.265/HEVC 编码
            android.media.MediaCodecList codecList = new android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS);
            for (android.media.MediaCodecInfo codecInfo : codecList.getCodecInfos()) {
                if (!codecInfo.isHardwareAccelerated()) {
                    continue;
                }
                if (!codecInfo.isEncoder()) {
                    continue;
                }
                if (!isSupported(codecInfo, "video/hevc")) {
                    continue;
                }
                SunshineServer.enableH265();
                return true;
            }
            State.log("设备不支持 H.265/HEVC 编码");
            return false;
        } catch (Exception e) {
            State.log("检查 H.265 编码支持时出错: " + e.getMessage());
            return false;
        }
    }

    private boolean isSupported(MediaCodecInfo codecInfo, String mime) {
        String[] types = codecInfo.getSupportedTypes();
        for (String type : types) {
            if (type.equalsIgnoreCase(mime)) {
                return true;
            }
        }
        return false;
    }

    public static Set<String> getAllWifiIpAddresses(Context context) {
        Set<String> ipAddresses = new HashSet<>();

        // 获取WiFi IP地址
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null && wifiManager.isWifiEnabled()) {
            int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
            if (ipAddress != 0) {
                // Convert little-endian to big-endian if needed
                byte[] bytes = new byte[4];
                bytes[0] = (byte) (ipAddress & 0xFF);
                bytes[1] = (byte) ((ipAddress >> 8) & 0xFF);
                bytes[2] = (byte) ((ipAddress >> 16) & 0xFF);
                bytes[3] = (byte) ((ipAddress >> 24) & 0xFF);

                try {
                    String ip = InetAddress.getByAddress(bytes).getHostAddress();
                    ipAddresses.add(ip);
                } catch (UnknownHostException e) {
                    Log.e(TAG, "获取WiFi IP地址失败", e);
                }
            }
        }

        // 获取所有网络接口的IP地址
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface ni = networkInterfaces.nextElement();
                if (ni.isUp() && !ni.isLoopback()) {
                    List<InterfaceAddress> interfaceAddresses = ni.getInterfaceAddresses();
                    for (InterfaceAddress interfaceAddress : interfaceAddresses) {
                        if (interfaceAddress.getAddress() != null) {
                            String ip = interfaceAddress.getAddress().getHostAddress();
                            if (ip != null && ip.startsWith("192.168")) {
                                ipAddresses.add(ip);
                            }
                        }
                    }
                }
            }
        } catch (SocketException e) {
            Log.e(TAG, "获取网络接口IP地址失败", e);
        }

        return ipAddresses;
    }

    public static void writeCertAndKey(Context context) throws CertificateException, NoSuchAlgorithmException, IOException, OperatorCreationException, InvalidAlgorithmParameterException {
        String absolutePath = context.getFilesDir().getAbsolutePath();
        File certFile = new File(absolutePath, "cacert.pem");
        File keyFile = new File(absolutePath, "cakey.pem");

        boolean needsGeneration = false;

        // 1. 首先判断文件是否存在
        if (!certFile.exists() || !keyFile.exists()) {
            needsGeneration = true;
        } else {
            try {
                // 1. 校验公钥 (证书)
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                cf.generateCertificate(new FileInputStream(certFile));

                // 2. 深度校验私钥 (PKCS#8 格式校验)
                String keyContent = new String(Files.readAllBytes(keyFile.toPath()));

                // 清理 PEM 标签和换行符，提取纯 Base64 字符串
                String privateKeyPEM = keyContent
                        .replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "")
                        .replace("-----BEGIN RSA PRIVATE KEY-----", "") // 兼容 RSA 格式
                        .replace("-----END RSA PRIVATE KEY-----", "")
                        .replaceAll("\\s+", ""); // 移除所有空白符

                byte[] encoded = Base64.decode(privateKeyPEM, Base64.DEFAULT);

                // 尝试用 PKCS8 规范解析 (这是 generateSelfSignedCertificate 常用的输出格式)
                PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
                KeyFactory kf = KeyFactory.getInstance("RSA"); // 如果你用的是 EC，这里换成 "EC"
                kf.generatePrivate(keySpec); // 如果格式错误或数据损坏，这里会抛出 InvalidKeySpecException

            } catch (Exception e) {
                Log.e("CertCheck", "证书或私钥非法，准备重新生成: " + e.getMessage());
                needsGeneration = true;
            }
        }

        // 3. 如果不存在或不合法，重新生成
        if (needsGeneration) {
            CertificateGenerator.generateSelfSignedCertificate(
                    certFile.getAbsolutePath(),
                    keyFile.getAbsolutePath()
            );
        }

        // 最后设置路径
        SunshineServer.setCertPath(certFile.getAbsolutePath());
        SunshineServer.setPkeyPath(keyFile.getAbsolutePath());
    }
}
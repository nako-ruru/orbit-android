package com.orbit;

import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.util.Log;

import com.easytier.jni.EasyTierJNI;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import aar.PrivateNetwork;
import aar.PrivateNetworkProvider;

public class AndroidPrivNetworkProvider implements PrivateNetworkProvider {

    private final Context context;
    private final String[] fixedIps;

    public AndroidPrivNetworkProvider(Context context, String[] fixedIps) {
        Log.i("AndroidPrivNetworkProvider", "AndroidPrivNetworkProvider");
        this.context = context.getApplicationContext();
        this.fixedIps = fixedIps;
    }

    @Override
    public PrivateNetwork newPrivateNetwork(String requestId, String easytierConf) throws Exception {
        return new AndroidPrivateNetwork(this.context, requestId, this.fixedIps, easytierConf);
    }
}

class AndroidPrivateNetwork implements PrivateNetwork {

    private final Context context;
    private final String[] fixedIps;
    private final String easytierConf;
    private final String requestId;

    // 核心：所有 JNI 操作共享同一个进程级别的静态锁！
    private static final Object JNI_LOCK = new Object();

    AndroidPrivateNetwork(Context context, String requestId, String[] fixedIps, String easytierConf) {
        this.context = context;
        this.fixedIps = fixedIps;
        this.easytierConf = easytierConf;
        this.requestId = requestId;
    }

    @Override
    public void start() throws Exception {
        // 1. JNI 启动实例（使用全局静态锁保护 C 底层全局状态）
        synchronized (JNI_LOCK) {
            int res = EasyTierJNI.runNetworkInstance(easytierConf);
            if (res != 0) {
                String error = EasyTierJNI.getLastError();
                throw new RuntimeException("网络实例启动失败: " + error);
            }
        }

        // 2. 检查并请求 VPN 权限（内部静态同步，无需外部加锁）
        CompletableFuture<Boolean> vpnFuture = PrivNetworkPermissionManager.requestPermissionIfNeeded(context);
        if (vpnFuture != null) {
            // 直接 get()，不再裹一层 try-catch！让 TimeoutException / InterruptedException 直接向上抛
            Boolean granted = vpnFuture.get(1, TimeUnit.MINUTES);
            if (!Boolean.TRUE.equals(granted)) {
                throw new IllegalStateException("用户拒绝了 VPN 权限授权");
            }
        }

        // 3. 启动前台服务
        Intent intent = new Intent(context, PrivNetworkService.class);
        intent.putExtra("FIXED_IPS", fixedIps);
        intent.putExtra("EASYTIER_CONFIG", easytierConf);
        intent.putExtra("REQUEST_ID", requestId);
        context.startForegroundService(intent);
    }

    @Override
    public void terminate() throws Exception {
        // 停止实例同样走全局静态 JNI 锁
        synchronized (JNI_LOCK) {
            stopSingleInstance(requestId);
        }
    }

    private int stopSingleInstance(String instanceName) throws JsonProcessingException {
        // 此方法已在 terminate() 的 JNI_LOCK 保护下，确保 list -> retain 过程原子化
        String instancesJson = EasyTierJNI.listInstances(1000);
        if (instancesJson == null || instancesJson.isBlank()) {
            return -1;
        }

        Map<String, String> instances = new ObjectMapper().readValue(
                instancesJson,
                new TypeReference<Map<String, String>>() {}
        );

        if (!instances.containsKey(instanceName)) {
            return -1;
        }

        List<String> instancesToRetain = new ArrayList<>();
        for (String name : instances.keySet()) {
            if (!instanceName.equals(name)) {
                instancesToRetain.add(name);
            }
        }

        return EasyTierJNI.retainNetworkInstance(
                instancesToRetain.toArray(new String[0])
        );
    }
}

class PrivNetworkPermissionManager {

    private static final Object LOCK = new Object();
    private static volatile CompletableFuture<Boolean> activeFuture = null;

    /**
     * 检查并请求 VPN 权限：
     * - 如果已经有权限，返回 null，调用方无需等待；
     * - 如果需要授权且未弹出 Activity，启动 Activity 并返回全新的 Future；
     * - 如果已经有在等待的授权弹窗，直接复用同一个 Future（多线程 A 共享等 1 个 B）。
     */
    public static CompletableFuture<Boolean> requestPermissionIfNeeded(Context context) {
        // 1. 系统级别检查：如果已经有权限，直接返回 null，不需要启动 Activity
        if (VpnService.prepare(context) == null) {
            return null;
        }

        // 2. 需要授权，加锁确保全局只启动一次 Activity 和创建一个 Future
        synchronized (LOCK) {
            if (activeFuture == null || activeFuture.isDone()) {
                activeFuture = new CompletableFuture<>();

                Intent intent = new Intent(context, TransparentProxyActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
            return activeFuture;
        }
    }

    /**
     * 透明 Activity 在授权结束/销毁时调用，唤醒所有挂在 Future 上的线程
     */
    public static void complete(boolean success) {
        synchronized (LOCK) {
            if (activeFuture != null && !activeFuture.isDone()) {
                activeFuture.complete(success);
            }
        }
    }
}
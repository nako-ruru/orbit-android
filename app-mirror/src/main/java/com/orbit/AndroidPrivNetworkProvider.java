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

    public static CompletableFuture<Object> future;

    public AndroidPrivNetworkProvider(Context context, String[] fixedIps) {
        Log.i("AndroidTunProvider", "AndroidTunProvider");
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

    private final Object lock = new Object();

    public static CompletableFuture<Object> future;

    AndroidPrivateNetwork(Context context, String requestId, String[] fixedIps, String easytierConf) {
        this.context = context;
        this.fixedIps = fixedIps;
        this.easytierConf = easytierConf;
        this.requestId = requestId;
    }

    @Override
    public void start() throws Exception {
        synchronized (lock) {
            int res = EasyTierJNI.runNetworkInstance(easytierConf);
            if (res != 0) {
                String error = EasyTierJNI.getLastError();
                throw new RuntimeException("网络实例启动失败: " + error);
            }
        }

        synchronized (lock) {
            // 1. 检查权限：如果返回不为 null，说明需要用户点击系统确认
            if (VpnService.prepare(context) != null) {
                // 激活透明 Activity 呼出系统弹窗
                Intent intent = new Intent(context, TransparentProxyActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                future = new CompletableFuture<>();
                context.startActivity(intent);
                future.get(1, TimeUnit.MINUTES);
            }
        }

        Intent intent = new Intent(context, NebulaService.class);
        intent.putExtra("FIXED_IPS", fixedIps);
        intent.putExtra("EASYTIER_CONFIG", easytierConf);
        intent.putExtra("REQUEST_ID", requestId);
        context.startForegroundService(intent);
    }

    @Override
    public void terminate() throws Exception {
        synchronized (lock) {
            stopSingleInstance(requestId);
        }
    }

    /**
     * 停止指定的 EasyTier 网络实例，其他实例保持运行。
     *
     * 实现逻辑：
     * 1. 通过 listInstances 获取当前运行的实例列表
     * 2. 排除需要停止的 instanceName
     * 3. 调用 retainNetworkInstance 保留剩余实例
     * 4. 被排除的实例会被 EasyTier 停止
     *
     * @param instanceName 要停止的实例名称
     * @return 0 表示成功，-1 表示失败
     */
    public int stopSingleInstance(String instanceName) throws JsonProcessingException {
        // 获取当前运行的实例
        String instancesJson = EasyTierJNI.listInstances(1000);
        if (instancesJson == null || instancesJson.isBlank()) {
            return -1;
        }

        /*
         * listInstances 返回：
         *
         * {
         *     "instance1": "instance-id-1",
         *     "instance2": "instance-id-2",
         *     "instance3": "instance-id-3"
         * }
         */
        Map<String, String> instances = new ObjectMapper().readValue(
                instancesJson,
                new TypeReference<Map<String, String>>() {}
        );

        // 如果目标实例不存在，直接认为操作失败
        if (!instances.containsKey(instanceName)) {
            return -1;
        }

        /*
         * 保留除目标实例之外的所有实例。
         *
         * 例如：
         *
         * 当前：
         * instance1
         * instance2
         * instance3
         *
         * stopSingleInstance("instance2")
         *
         * 最终：
         * retainNetworkInstance(["instance1", "instance3"])
         *
         * instance2 就会被停止。
         */
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

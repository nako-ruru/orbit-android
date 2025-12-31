package com.orbit;

import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import aar.TunProvider;

public class AndroidTunProvider implements TunProvider {

    private final Context context;
    private final String[] fixedIps;

    public static CompletableFuture<Object> future;
    private final Object lock = new Object();

    @Override
    public long getTunId() throws Exception {
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

        synchronized (lock) {
            future = new CompletableFuture<>();
            Intent intent = new Intent(context, NebulaService.class);
            intent.putExtra("FIXED_IPS", fixedIps);
            context.startForegroundService(intent);
            future.get(10, TimeUnit.SECONDS);
            ParcelFileDescriptor pfd = NebulaService.vpnInterface;
            if (pfd != null) {
                return pfd.getFd(); // 成功：返回 FD 供 Go 读写数据包
            }
        }

        throw new Exception("getTunId failed");
    }

    public AndroidTunProvider(Context context, String[] fixedIps) {
        this.context = context.getApplicationContext();
        this.fixedIps = fixedIps;
    }
}

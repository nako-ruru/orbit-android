package com.orbit;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PermissionManager {

    // 定义回调接口
    public interface PermissionCallback {
        void onResult(boolean allGranted, Map<String, Boolean> details);
    }

    private final ComponentActivity activity;
    private final ActivityResultLauncher<String[]> launcher;

    private boolean isRequesting = false;
    private final List<PermissionRequest> pendingRequests = new ArrayList<>();

    public PermissionManager(ComponentActivity activity) {
        this.activity = activity;
        // 在构造时注册，必须在 onCreate 或生命周期早期调用
        this.launcher = activity.registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                this::handleResult
        );
    }

    /**
     * 外部调用的入口
     */
    public synchronized void request(String[] permissions, PermissionCallback callback) {
        // 1. 过滤掉已经拥有的权限
        List<String> needed = new ArrayList<>();
        for (String p : permissions) {
            if (ContextCompat.checkSelfPermission(activity, p) != PackageManager.PERMISSION_GRANTED) {
                needed.add(p);
            }
        }

        // 如果全部已授权，直接回调
        if (needed.isEmpty()) {
            if (callback != null) callback.onResult(true, new HashMap<>());
            return;
        }

        // 2. 加入待处理任务
        pendingRequests.add(new PermissionRequest(needed, callback));

        // 3. 如果当前没有正在进行的授权，立即开始
        if (!isRequesting) {
            processNextBatch();
        }
    }

    // 3. 专门请求 VPN 的方法
    public void requestVpn() {
        Intent vpnIntent = VpnService.prepare(context);
        if (vpnIntent != null) {
            if (!isRequesting) {
                isRequesting = true;
                vpnLauncher.launch(vpnIntent); // 弹出 VPN 专用对话框
            } else {
                // 如果正在授权别的，可以先把 VPN 请求标记一下，稍后处理
            }
        } else {
            // 已经有权限了
        }
    }

    private synchronized void processNextBatch() {
        if (pendingRequests.isEmpty()) return;

        isRequesting = true;

        // 4. 合并当前队列中所有的权限请求
        Set<String> allToRequest = new HashSet<>();
        for (PermissionRequest request : pendingRequests) {
            allToRequest.addAll(request.permissions);
        }

        launcher.launch(allToRequest.toArray(new String[0]));
    }

    private synchronized void handleResult(Map<String, Boolean> results) {
        isRequesting = false;

        // 5. 分发结果给对应的回调
        List<PermissionRequest> handled = new ArrayList<>(pendingRequests);
        pendingRequests.clear();

        for (PermissionRequest request : handled) {
            boolean allGranted = true;
            Map<String, Boolean> requestResults = new HashMap<>();

            for (String p : request.permissions) {
                boolean granted = results.containsKey(p) ? results.get(p) :
                        ContextCompat.checkSelfPermission(activity, p) == PackageManager.PERMISSION_GRANTED;
                requestResults.put(p, granted);
                if (!granted) allGranted = false;
            }

            if (request.callback != null) {
                request.callback.onResult(allGranted, requestResults);
            }
        }

        // 6. 处理处理过程中新加进来的请求（如果有的话）
        if (!pendingRequests.isEmpty()) {
            processNextBatch();
        }
    }

    // 内部类：包装请求
    private static class PermissionRequest {
        List<String> permissions;
        PermissionCallback callback;

        PermissionRequest(List<String> permissions, PermissionCallback callback) {
            this.permissions = permissions;
            this.callback = callback;
        }
    }
}

package com.orbit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;

/*
public class VpnPermissionActivity extends Activity {

    private static final int REQ_VPN = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. 展示您的自定义合规话术对话框
        new AlertDialog.Builder(this)
                .setTitle("开启加密传输通道")
                .setMessage("为了确保远程数据安全，我们需要建立一条端到端的加密专线。该通道仅用于私有办公网段，不影响普通上网。")
                .setCancelable(false)
                .setPositiveButton("立即开启", (dialog, which) -> {
                    // 2. 触发真正的系统 VPN 授权弹窗
                    Intent intent = VpnService.prepare(VpnPermissionActivity.this);
                    if (intent != null) {
                        startActivityForResult(intent, 1024);
                    } else {
                        onActivityResult(1024, RESULT_OK, null);
                    }
                })
                .setNegativeButton("稍后再说", (dialog, which) -> {
                    TunDriverImpl.onAuthFailed(); // 通知 Go 层授权失败
                    finish();
                })
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1024 && resultCode == RESULT_OK) {
            // 用户点击了系统窗口的“允许”
            TunDriverImpl.onAuthorized();
        } else {
            TunDriverImpl.onAuthFailed();
        }
        finish();
    }

    private void startVpn() {
        Intent i = new Intent(this, NebulaService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(i);
        } else {
            startService(i);
        }
    }
}
 */
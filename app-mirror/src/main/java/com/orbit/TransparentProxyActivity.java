package com.orbit;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.util.Log;

/**
 * 这是一个透明的任务容器，只负责接听系统的“授权成功”电话。
 * 拿到结果后立即自毁，不留任何痕迹。
 */
public class TransparentProxyActivity extends Activity {

    private static final int REQUEST_PN_CODE = 0x66;
    private boolean isNotified = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = VpnService.prepare(this);
        if (intent != null) {
            startActivityForResult(intent, REQUEST_PN_CODE);
        } else {
            // 已经有权限，直接通知成功并关闭
            notifyResultAndFinish(true);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PN_CODE) {
            boolean isGranted = (resultCode == RESULT_OK);
            if (isGranted) {
                Log.d("Secure", "VPN Permission Granted by User");
            } else {
                Log.w("Secure", "VPN Permission Denied by User");
            }
            // 准确传递授权结果 (true / false)
            notifyResultAndFinish(isGranted);
        } else {
            notifyResultAndFinish(false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 兜底逻辑：如果用户按 Back 键或系统强杀 Activity，确保通知等待方“失败”，防止卡死
        if (!isNotified) {
            notifyResultAndFinish(false);
        }
    }

    private void notifyResultAndFinish(boolean success) {
        if (!isNotified) {
            isNotified = true;
            PrivNetworkPermissionManager.complete(success);
        }
        if (!isFinishing()) {
            finish();
        }
    }
}
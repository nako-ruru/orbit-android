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
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. 立即检查系统权限
        Intent intent = VpnService.prepare(this);
        if (intent != null) {
            // 2. 弹出系统自带的那个【确定/取消】对话框
            startActivityForResult(intent, 0x66);
        } else {
            // 已经有权限了，直接关掉
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 0x66 && resultCode == RESULT_OK) {
            // 3. 用户点了“允许”！
            // 这里可以发一个简单的广播或者直接调用 Go 侧的启动函数
            Log.d("VPN", "Permission Granted by User");
        }
        finish(); // 任务完成，自毁
    }
}
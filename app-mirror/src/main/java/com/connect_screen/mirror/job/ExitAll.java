package com.connect_screen.mirror.job;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.connect_screen.mirror.TouchpadAccessibilityService;
import com.connect_screen.mirror.shizuku.ShizukuUtils;
import com.orbit.MainActivity;
import com.orbit.SplashActivity;

public class ExitAll {
    public static void execute(Context context, boolean restart) {
        if (TouchpadAccessibilityService.getInstance() != null && ShizukuUtils.hasPermission()) {
            // 下次可自动获取
            TouchpadAccessibilityService.getInstance().disableSelf();
        }
        if(MainActivity.activity != null) {
            MainActivity.activity.finish();
        }

        restartByAlarm(MainActivity.activity, SplashActivity.class);
    }

    public static void restartByAlarm(Context context, Class<?> cls) {
        Log.i("ExitAll", "restartByAlarm: context: " + context);
        Log.i("ExitAll", "restartByAlarm: context.getPackageManager(): " + context.getPackageManager());
        // 1. 自动获取应用的启动入口（Launch Activity）
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (intent != null) {
            // 2. 清除任务栈，确保是一次彻头彻尾的“冷启动”
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

            // 3. 处理 Android 12+ 的 PendingIntent Flag 崩溃问题
            int flags = PendingIntent.FLAG_CANCEL_CURRENT;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }

            PendingIntent pIntent = PendingIntent.getActivity(context, 123456, intent, flags);

            // 4. 设置闹钟并在 500ms 后触发
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            alarmManager.set(AlarmManager.RTC, System.currentTimeMillis() + 500, pIntent);

            // 5. 立即退出进程，释放所有资源（包括 C 库内存）
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(0);
        }
    }
}

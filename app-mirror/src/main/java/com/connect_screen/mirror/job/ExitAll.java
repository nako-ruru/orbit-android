package com.connect_screen.mirror.job;

import android.app.ActivityOptions;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

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
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

            // 1. 设置 BAL 允许模式
            ActivityOptions options = ActivityOptions.makeBasic();
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14+
                options.setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                );
            }

            // 2. 处理 PendingIntent Flags
            int flags = PendingIntent.FLAG_CANCEL_CURRENT;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }

// 在你的 flags 基础上进行位或运算
            int updatedFlags = flags;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                // 除非明确需要修改 Intent 内容，否则一律使用 FLAG_IMMUTABLE
                updatedFlags |= PendingIntent.FLAG_IMMUTABLE;
            }

            // 3. 【关键修改】在 getActivity 创建时就传入 options.toBundle()
            // 这解决了日志中提到的 "missing opt in by PI creator" 问题
            PendingIntent pIntent = PendingIntent.getActivity(
                    context,
                    123456,
                    intent,
                    updatedFlags, // 使用更新后的 flags
                    options.toBundle()
            );

            // 4. 设置闹钟 (不再手动调用 pIntent.send())
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null) {
                long triggerAtMillis = System.currentTimeMillis() + 500;
                // 5. 关键调用：即使省电模式也强制执行
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pIntent);
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pIntent);
                }
            }

            // 5. 退出进程
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(0);
        }
    }
}

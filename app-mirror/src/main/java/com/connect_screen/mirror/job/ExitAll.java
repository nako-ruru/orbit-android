package com.connect_screen.mirror.job;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import com.connect_screen.mirror.BuildConfig;
import com.connect_screen.mirror.State;
import com.connect_screen.mirror.SunshineService;
import com.connect_screen.mirror.TouchpadAccessibilityService;
import com.connect_screen.mirror.shizuku.ShizukuUtils;
import com.orbit.MainActivity;
import com.orbit.SplashActivity;

public class ExitAll {
    public static void execute(Context context, boolean restart) {
        if(true) {
            if (TouchpadAccessibilityService.getInstance() != null && ShizukuUtils.hasPermission()) {
                // 下次可自动获取
                TouchpadAccessibilityService.getInstance().disableSelf();
            }
            if(MainActivity.activity != null) {
                MainActivity.activity.finish();
            }

            restartByAlarm(context, SplashActivity.class);
            return;
        }

        if (SunshineService.instance != null) {
            SunshineService.instance.releaseWakeLock();
        }
        boolean wasSunshineStarted = SunshineServer.exitServer();
        CreateVirtualDisplay.restoreAspectRatio();
        InputRouting.moveImeToDefault();
        SunshineAudio.restoreVolume(context);
        State.unbindUserService();
        if (State.mediaProjectionInUse != null) {
            State.mediaProjectionInUse.stop();
            State.mediaProjectionInUse = null;
        }
        State.setMediaProjection(null);
        // 重启应用
        if (restart) {
            PackageManager packageManager = context.getPackageManager();
            Intent intent = packageManager.getLaunchIntentForPackage(BuildConfig.APPLICATION_ID);
            ComponentName componentName = intent.getComponent();
            Intent mainIntent = Intent.makeRestartActivityTask(componentName);
            // Required for API 34 and later
            // Ref: https://developer.android.com/about/versions/14/behavior-changes-14#safer-intents
            mainIntent.setPackage(context.getPackageName());
            mainIntent.putExtra("DoNotAutoStartMoonlight", true);
            context.startActivity(mainIntent);
        }

        if (State.mirrorVirtualDisplay != null) {
            State.mirrorVirtualDisplay.release();
        }

        State.displaylinkState.destroy();

        if (!wasSunshineStarted && !ShizukuUtils.hasPermission() && TouchpadAccessibilityService.getInstance() != null) {
            // 对于 typec 直连，但是只用无障碍的用户不退无障碍
            if (State.getCurrentActivity() != null) {
                State.getCurrentActivity().finish();
            }
//            return;
        }
        if (TouchpadAccessibilityService.getInstance() != null && ShizukuUtils.hasPermission()) {
            // 下次可自动获取
            TouchpadAccessibilityService.getInstance().disableSelf();
        }
        if (context != null) {
            context.stopService(new Intent(context, SunshineService.class));
        }
        // 退出应用进程
        Log.i("ExitAll", "System.exit(0);");
        System.exit(0);
        try {
            android.os.Process.killProcess(android.os.Process.myPid());
        } catch(Throwable e) {
            // ignore
        }
    }

    public static void restartByAlarm(Context context, Class<?> cls) {
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

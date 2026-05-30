package com.connect_screen.mirror.job;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.connect_screen.mirror.State;

public class ExitAll {
    public static void execute(Context context, boolean restart) {
		Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        // 关键：不要只传 Intent，要让系统觉得这是一个来自闹钟的正式提醒
        PendingIntent pIntent = PendingIntent.getActivity(context, 123456, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_CANCEL_CURRENT);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        // 使用 setAlarmClock，这是优先级最高的闹钟类型，
        // 系统会为了这个闹钟专门准备 CPU 资源，不容易被冻结。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(System.currentTimeMillis() + 0, pIntent);
            alarmManager.setAlarmClock(info, pIntent);
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 0, pIntent);
        }

        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }
}

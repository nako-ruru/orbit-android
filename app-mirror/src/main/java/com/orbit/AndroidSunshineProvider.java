package com.orbit;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.connect_screen.mirror.State;
import com.connect_screen.mirror.SunshineService;
import com.connect_screen.mirror.job.SunshineServer;

import aar.SunshineProvider;

public class AndroidSunshineProvider implements SunshineProvider {
    private final Context context;

    public AndroidSunshineProvider(Context context) {
        this.context = context;
    }

    @Override
    public void start() {
        requestStart(context);
    }

    @Override
    public boolean inputPin(String pin, String name) throws Exception {
//        SunshineServer.onPinRequested();
        return true;
    }

    // 静态存储授权后的数据，只要进程不死就一直有效
    public static Intent cachedData = null;

    /**
     * Golang 调用此方法: 一键启动投屏
     */
    public static void requestStart(Context context) {
        Intent intent = new Intent(context, ProjectionPermissionActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // 确保从非 Activity 环境也能启动
        context.startActivity(intent);
    }

    // 内部统一启动 Service 方法
    public static void launchService(Context context, Intent data) {
        Intent serviceIntent = new Intent(context, SunshineService.class);
        serviceIntent.putExtra("data", data);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }
}

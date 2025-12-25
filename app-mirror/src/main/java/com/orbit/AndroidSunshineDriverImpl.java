package com.orbit;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.connect_screen.mirror.State;
import com.connect_screen.mirror.SunshineService;

import aar.SunshineAndroidDriver;

public class AndroidSunshineDriverImpl implements SunshineAndroidDriver {
    private final Context context;

    public AndroidSunshineDriverImpl(Context context) {
        this.context = context;
    }

    @Override
    public void start() throws Exception {
        Intent sunshineServiceIntent = new Intent(context, SunshineService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(sunshineServiceIntent);
        } else {
            context.startService(sunshineServiceIntent);
        }
        State.log("启动 SunshineService 服务");
    }
}

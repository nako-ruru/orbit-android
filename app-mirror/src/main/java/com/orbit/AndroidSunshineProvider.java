package com.orbit;

import android.content.Context;

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
        SunshineServer.submitPin(pin);
        return true;
    }

    /**
     * Golang 调用此方法: 一键启动投屏
     */
    public void requestStart(Context context) {

    }
}

package com.orbit;

import android.app.Application;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;

import aar.Aar;

public class OrbitApplication  extends Application {
    @Override
    public void onCreate() {
        // 1. 注册驱动 (驱动持有 ApplicationContext 是安全的)
        Aar.registerAndroidDriver(new AndroidDriverImpl(this));
        Aar.registerSunshineDriver(new AndroidSunshineDriverImpl(this));
        Aar.setConfigDir(this.getFilesDir().getAbsolutePath());

        // 2. 在子线程中启动 Go 逻辑，避免阻塞主线程（UI 线程）
        new Thread(() -> {
            try {
                // 读取配置文件
                InputStream is = getAssets().open("daemon.yml");
                byte[] data = new byte[is.available()];
                is.read(data);
                is.close();

                // 启动 Go 业务
                // 注意：如果 start 内部调用了 w.Run()，它会在这里阻塞，直到第一个 WebView 销毁
                aar.Aar.startService(data);

            } catch (IOException e) {
                Log.e("OrbitSDK", "Failed to load assets", e);
            }
        }).start();
        new Thread(() -> {
            try {
                // 读取配置文件
                InputStream is = getAssets().open("orbit.yml");
                byte[] data = new byte[is.available()];
                is.read(data);
                is.close();

                // 启动 Go 业务
                // 注意：如果 start 内部调用了 w.Run()，它会在这里阻塞，直到第一个 WebView 销毁
                aar.Aar.start(data);

            } catch (IOException e) {
                Log.e("OrbitSDK", "Failed to load assets", e);
            }
        }).start();
    }
}
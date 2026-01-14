package com.orbit;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import aar.Aar;

public class OrbitApplication  extends Application {

    private boolean isInitialized;
    public static Activity activity;

    @Override
    public void onCreate() {
        Log.i("LIFECYCLE", "OrbitApplication.onCreate");
        super.onCreate();

        String id = "500";
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                OrbitApplication.activity = activity;
                Log.i("OrbitApplication", "onActivityCreated: " + activity);
                if (!isInitialized) {
                    test(activity, id);
                    isInitialized = true;
                }
            }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                Log.i("OrbitApplication", "onActivityResumed: " + activity);
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle bundle) {
            }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
                Log.i("OrbitApplication", "onActivityDestroyed: " + activity);
            }
        });
    }

    private static void test(Activity context, String id) {
        // 1. 注册驱动 (驱动持有 ApplicationContext 是安全的)
        Aar.registerWebViewDriver(new AndroidWebViewProvider(context));
        Aar.registerSunshineProvider(new AndroidSunshineProvider(context));
        Aar.registerDeviceInfoProvider(new AndroidDeviceInfoProvider(context));
        Aar.registerFSProvider(new AndroidWebdavProvider(context));
        File filesDir = context.getFilesDir();
        Aar.setConfigDir(filesDir.getAbsolutePath());
        Aar.setTempDir(context.getCacheDir().getAbsolutePath());

        List<String> fixedIpList = new ArrayList<>();
        Random random = new Random();
        /*
        for(int i = 0; i < 256; i++) {
            int v;
            for (;;) {
                v = random.nextInt(256);
                if (v != 0 && v != 1 && v != 255) {
                    break;
                }
            }
            String ip = String.format("10.133.%d.%d", i, v);
            fixedIpList.add(ip);
        }*/
        fixedIpList.add("10.249.128.128");
        String[] fixedIpArray = fixedIpList.toArray(new String[0]);
        AndroidTunProvider tunProvider = new AndroidTunProvider(context, fixedIpArray);
        Aar.registerTunProvider(tunProvider);

        Intent intent = new Intent(context, StreamerService.class);
        context.startForegroundService(intent);

        new Thread(() -> {
            try {
                tunProvider.getTunId();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).start();
        // 2. 在子线程中启动 Go 逻辑，避免阻塞主线程（UI 线程）
        new Thread(() -> {
            try {
                // 读取配置文件
                InputStream is = context.getAssets().open("daemon.yml");
                YAMLMapper mapper = new YAMLMapper();
                ObjectNode root = (ObjectNode) mapper.readTree(is);
                is.close();
                ObjectNode vpn = (ObjectNode) root.get("vpn");
                if (vpn == null) {
                    vpn = root.set("vpn", vpn);
                }
                ArrayNode arr = mapper.createArrayNode();
                for (String ip : fixedIpList) {
                    arr.add(ip);
                }
                vpn.set("fixed-ips", arr);
                byte[] data = mapper.writeValueAsBytes(root);
                Aar.startService(data);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).start();
        new Thread(() -> {
            try {
                // 读取配置文件
                InputStream is =  context.getAssets().open("orbit.yml");
                byte[] data = new byte[is.available()];
                is.read(data);
                is.close();
                Aar.start(data);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).start();
    }
}
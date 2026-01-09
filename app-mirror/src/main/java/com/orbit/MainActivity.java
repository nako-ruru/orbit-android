package com.orbit;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import aar.Aar;

public class MainActivity extends Activity {

    public static void test(Activity context, String id) {
        // 1. 注册驱动 (驱动持有 ApplicationContext 是安全的)
        Aar.registerWebViewDriver(new AndroidWebViewProvider(context));
        Aar.registerSunshineProvider(new AndroidSunshineProvider(context));
        Aar.registerDeviceInfoProvider(new AndroidDeviceInfoProvider(context));
        File filesDir = context.getFilesDir();
        Aar.setConfigDir(filesDir.getAbsolutePath());
        Aar.setTempDir(context.getCacheDir().getAbsolutePath());

        List<String> fixedIpList = new ArrayList<>();
        Random random = new Random();
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
        }
        fixedIpList.add("10.249.128.128");
        String[] fixedIpArray = fixedIpList.toArray(new String[0]);
        AndroidTunProvider tunProvider = new AndroidTunProvider(context, fixedIpArray);
        Aar.registerTunProvider(tunProvider);
        new Thread(() -> {
            try {
//                tunProvider.getTunId();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).start();

        // 2. 在子线程中启动 Go 逻辑，避免阻塞主线程（UI 线程）
        new Thread(() -> {
            try {
                // 读取配置文件
                InputStream is = context.getAssets().open("daemon.yml");
                YAMLMapper yaml = new YAMLMapper();
                AppConfig config = yaml.readValue(is, AppConfig.class);
                is.close();
                config.vpn.fixedIps = fixedIpArray;
                byte[] data = yaml.writeValueAsBytes(config);
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

    @Override
    protected void onCreate(Bundle s) {
        Log.i("LIFECYCLE", "MainActivity.onCreate");
        super.onCreate(s);
//        this.test();
    }

    public static class AppConfig {
        /* ===================== 根配置 ===================== */
        public Map<String, String> vars;
        @JsonProperty("log-dir")
        public String logDir;
        @JsonProperty("desktop-signal-url")
        public String desktopSignalUrl;
        public String api;
        public SunshineConf sunshine;
        public VpnConf vpn;
        public DaemonConf daemon;
        public SessionConf session;
        public StreamerConf streamer;
        public NpcConf npc;

        /* ===================== VpnConf ===================== */

        public static class VpnConf {
            @JsonProperty("path")
            public String vpnPath;
            @JsonProperty("fixed-ips")
            public String[] fixedIps;
        }

        /* ===================== SunshineConf ===================== */

        public static class SunshineConf {

            public String path;

            @JsonProperty("config")
            public String configPath;

            @JsonProperty("test-authentication")
            public String testAuthentication;
        }

        /* ===================== DaemonConf ===================== */

        public static class DaemonConf {

            public String name;

            @JsonProperty("display-name")
            public String displayName;

            @JsonProperty("desc")
            public String description;
        }

        /* ===================== SessionConf ===================== */

        public static class SessionConf {

            @JsonProperty("stream-timeout")
            public String streamTimeout;

            @JsonProperty("heartbeat-timeout")
            public String heartbeatTimeout;
        }

        /* ===================== StreamerConf ===================== */
        // 对应 streamer.Config
        public static class StreamerConf {
            public String dir;
        }

        /* ===================== NpcConf ===================== */

        public static class NpcConf {

            @JsonProperty("path")
            public String path;

            @JsonProperty("nps-addr")
            public String npsAddr;
        }
    }

}
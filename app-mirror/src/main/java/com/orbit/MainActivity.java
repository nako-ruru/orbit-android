package com.orbit;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;

import aar.Aar;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);

        // 1. 注册驱动 (驱动持有 ApplicationContext 是安全的)
        Aar.registerWebViewDriver(new AndroidWebViewProvider(this));
        Aar.registerSunshineProvider(new AndroidSunshineProvider(this));
        Random random = new Random();
        String[] fixedIps = IntStream.range(0, 256)
                .mapToObj(i -> {
                    int v;
                    for (;;) {
                        v = random.nextInt(256);
                        if (v != 0 && v != 1 && v != 255) {
                            break;
                        }
                    }
                    return String.format("10.133.%d.%d", i, v);
                })
                .toArray(String[]::new);
        Aar.registerTunProvider(new AndroidTunProvider(this, fixedIps));
        Aar.setConfigDir(this.getFilesDir().getAbsolutePath());
        Aar.setTempDir(this.getCacheDir().getAbsolutePath());

        // 2. 在子线程中启动 Go 逻辑，避免阻塞主线程（UI 线程）
        new Thread(() -> {
            try {
                // 读取配置文件
                InputStream is = getAssets().open("daemon.yml");
                YAMLMapper yaml = new YAMLMapper();
                AppConfig config = yaml.readValue(is, AppConfig.class);
                config.vpn.fixedIps = fixedIps;
                is.close();
                byte[] data = yaml.writeValueAsBytes(config);
                Aar.startService(data);
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
                finish();
                Aar.start(data);

            } catch (IOException e) {
                Log.e("OrbitSDK", "Failed to load assets", e);
            }
        }).start();
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
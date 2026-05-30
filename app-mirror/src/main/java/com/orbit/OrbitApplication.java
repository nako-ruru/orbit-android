package com.orbit;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;

import com.connect_screen.mirror.State;
import com.connect_screen.mirror.job.ExitAll;
import com.connect_screen.mirror.job.SunshineServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.pivovarit.function.ThrowingRunnable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import aar.Aar;
import fi.iki.elonen.NanoHTTPD;

public class OrbitApplication  extends Application {

    private boolean isInitialized;
    public static Reference<Activity> activity;
    private static AndroidClipboardProvider clipboardProvider;

    @Override
    public void onCreate() {
        Log.i("LIFECYCLE", "OrbitApplication.onCreate");
        super.onCreate();

        String id = "500";
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                OrbitApplication.activity = new WeakReference<>(activity);
                Log.i("OrbitApplication", "onActivityCreated: " + activity);
                if (!isInitialized) {
                    isInitialized = true;
                }
            }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
                Log.i("OrbitApplication", "onActivityStarted: " + activity);
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                Log.i("OrbitApplication", "onActivityResumed: " + activity);
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
                Log.i("OrbitApplication", "onActivityPaused: " + activity);
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
                Log.i("OrbitApplication", "onActivityStopped: " + activity);
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle bundle) {
                Log.i("OrbitApplication", "onActivitySaveInstanceState: " + activity);
            }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
                Log.i("OrbitApplication", "onActivityDestroyed: " + activity);
            }
        });


        // 1. 注册驱动 (驱动持有 ApplicationContext 是安全的)
        Aar.registerWebViewDriver(new AndroidWebViewProvider(this));
        Aar.registerSunshineProvider(new AndroidSunshineProvider(this));
        Aar.registerDeviceInfoProvider(new AndroidDeviceInfoProvider(this));
        Aar.registerFSProvider(new AndroidWebdavProvider(this));
        Aar.registerPathProvider(new AndroidPathProvider(this));
        Aar.registerFileTransferProvider(new AndroidFileTransferProvider(this));
        Aar.registerStreamerProvider(new AndroidStreamerProvider(this));
        Aar.registerClipboardProvider(clipboardProvider = new AndroidClipboardProvider(this));
        startKeepAliveService();
    }

    public static void test(Context context, String id) {
        clipboardProvider.watch0();

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
//        fixedIpList.add("10.249.128.128");

        new Thread(ThrowingRunnable.sneaky(() -> {
            String[] fixedIpArray = fixedIpList.toArray(new String[0]);
            AndroidTunProvider tunProvider = new AndroidTunProvider(context, fixedIpArray);
            Aar.registerTunProvider(tunProvider);

            NanoHTTPD httpd = new NanoHTTPD(9723) {
                @Override
                public Response serve(IHTTPSession session) {
                    Log.d("TestServer", "serve");
                    // 获取 URL 路径，例如 /trigger
                    String uri = session.getUri();
                    // 获取参数，例如 ?cmd=open
                    java.util.Map<String, String> params = session.getParms();

                    if ("/trigger".equals(uri)) {
                        // --- 在这里执行你的测试逻辑 ---
                        ExitAll.execute(context, true);
                    } else if("/resolution".equals(uri)) {
                        SunshineServer.raiseResolutionChange(1280, 576);
                    }

                    return NanoHTTPD.newFixedLengthResponse(  Response.Status.NO_CONTENT,   "text/plain","")  ;
                }
            };
            httpd.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            Log.d("TestServer", "Server 已启动，监听端口 9723");

            File writableDir = new File(context.getFilesDir(), "streamer/static");
            if (writableDir.exists()) {
                writableDir.delete();
            }
            StreamerService.copyAssetsFolder(context, "streamer/static", writableDir);
            /*
            Intent intent = new Intent(context, StreamerService.class);
            context.startForegroundService(intent);
             */
        })).start();

        /*
        new Thread(() -> {
            try {
                tunProvider.getTunId();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).start();
         */

        // 2. 在子线程中启动 Go 逻辑，避免阻塞主线程（UI 线程）
        new Thread(ThrowingRunnable.sneaky(() -> {
            Map<String, Object> requireModified = new HashMap<>();
            requireModified.put("vpn.fixed-ips", fixedIpList);
            String nativeLibraryDir = context.getApplicationInfo().nativeLibraryDir;
            requireModified.put("streamer.path", new File(nativeLibraryDir, "libweb-server.so").getAbsoluteFile());
            requireModified.put("streamer.streamer-path", new File(nativeLibraryDir, "libstreamer.so").getAbsoluteFile());
            requireModified.put("streamer.workspace", new File(context.getFilesDir(), "streamer").getAbsoluteFile());
            requireModified.put("npc.path", new File(nativeLibraryDir, "libnpc.so").getAbsoluteFile());
            // 读取配置文件
            InputStream is = context.getAssets().open("daemon.yml");
            byte[] data = OrbitApplication.updateConfig(is, requireModified);
            is.close();
            Log.i("OrbitApplication", "Aar.runDaemon(data)");
            Aar.runDaemon(data);
        })).start();
    }

    private void startKeepAliveService() {
        Intent serviceIntent = new Intent(this, KeepAliveService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    /**
     * 修改任意层次的配置
     * @param in 输入流（可以是 JSON 或 YAML）
     * @param values 修改项，key 支持点号分隔层级，如 "server.port"
     * @return 修改后的 YAML 字符串
     */
    public static byte[] updateConfig(InputStream in, Map<String, Object> values) throws IOException {
        ObjectMapper yamlMapper = new YAMLMapper();

        // 1. 解析为 JsonNode 树
        JsonNode root = yamlMapper.readTree(in);

        // 2. 遍历 values 进行批量修改
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            updateHierarchicalValue(yamlMapper, root, entry.getKey(), entry.getValue());
        }

        // 3. 写回字符串
        return yamlMapper.writeValueAsBytes(root);
    }

    /**
     * 递归或循环查找并修改节点
     */
    private static void updateHierarchicalValue(ObjectMapper yamlMapper, JsonNode root, String path, Object newValue) {
        String[] keys = path.split("\\.");
        JsonNode currentNode = root;

        // 遍历路径，直到找到最后一个 key 的父节点
        for (int i = 0; i < keys.length - 1; i++) {
            // 检查当前节点
            currentNode = currentNode.path(keys[i]);

            // 如果当前节点是 MissingNode 或者 不是 ObjectNode 类型，就创建新的 ObjectNode
            if (currentNode.isMissingNode()) {
                ObjectNode newObjectNode = yamlMapper.createObjectNode();
                ((ObjectNode) root).set(keys[i], newObjectNode);  // 在父节点上设置新节点
                currentNode = newObjectNode;  // 更新 currentNode 为新创建的节点
            } else if (!currentNode.isObject()) {
                // 如果当前节点存在但不是 ObjectNode 类型，则抛出异常
                throw new RuntimeException("路径节点不是对象结构: " + keys[i]);
            }
        }

        // 找到最后一个节点所在的 ObjectNode
        if (currentNode instanceof ObjectNode) {
            ObjectNode parentNode = (ObjectNode) currentNode;
            String lastKey = keys[keys.length - 1];

            // 使用 valueToTree 自动处理各种数据类型 (String, Integer, Boolean, List, Map 等)
            parentNode.set(lastKey, yamlMapper.valueToTree(newValue));
        } else {
            // 如果最后的节点不是 ObjectNode，则抛出异常
            throw new RuntimeException("路径的最后一个节点不是对象结构: " + keys[keys.length - 1]);
        }
    }
}
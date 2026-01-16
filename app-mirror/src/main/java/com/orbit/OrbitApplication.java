package com.orbit;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;

import com.connect_screen.mirror.job.ExitAll;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import aar.Aar;
import fi.iki.elonen.NanoHTTPD;

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
                Log.d("Tracker", activity.getClass().getSimpleName() + " 被销毁");
                Log.d("Tracker", "是否因为配置更改重启: " + activity.isChangingConfigurations());

            }
        });

        NanoHTTPD  server = new NanoHTTPD(9723) {
            @Override
            public Response serve(IHTTPSession session) {
                // 获取 URL 路径，例如 /trigger
                String uri = session.getUri();
                // 获取参数，例如 ?cmd=open
                java.util.Map<String, String> params = session.getParms();

                if ("/trigger".equals(uri)) {
                    String cmd = params.get("cmd");

                    // --- 在这里执行你的测试逻辑 ---
                    ExitAll.execute(OrbitApplication.this, true);

                    return newFixedLengthResponse("Android 已接收指令: " + cmd);
                }

                return newFixedLengthResponse("Server 正在运行，但路径不匹配");
            }
        };
        try {
            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            Log.d("TestServer", "Server 已启动，监听端口 9723");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void test(Activity context, String id) {
        // 1. 注册驱动 (驱动持有 ApplicationContext 是安全的)
        Aar.registerWebViewDriver(new AndroidWebViewProvider(context));
        Aar.registerSunshineProvider(new AndroidSunshineProvider(context));
        Aar.registerDeviceInfoProvider(new AndroidDeviceInfoProvider(context));
        Aar.registerFSProvider(new AndroidWebdavProvider(context));
        Aar.registerPathProvider(new AndroidPathProvider(context));
        Aar.registerFileTransferProvider(new AndroidFileTransferProvider(context));

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

        /*
        Intent intent = new Intent(context, StreamerService.class);
        context.startForegroundService(intent);
         */

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
                Map<String, Object> requireModified = new HashMap<>();
                requireModified.put("vpn.fixed-ips", fixedIpList);
                String nativeLibraryDir = context.getApplicationInfo().nativeLibraryDir;
                requireModified.put("streamer.path", new File(nativeLibraryDir, "libweb-server.so").getAbsoluteFile());
                requireModified.put("streamer.streamer-path", new File(nativeLibraryDir, "libstreamer.so").getAbsoluteFile());
                requireModified.put("streamer.workspace", new File(context.getFilesDir(), "streamer").getAbsoluteFile());
                // 读取配置文件
                InputStream is = context.getAssets().open("daemon.yml");
                byte[] data = OrbitApplication.updateConfig(is, requireModified);
                is.close();
                Aar.runDaemon(data);
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
                Aar.runOrbit(data);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).start();
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
            currentNode = currentNode.path(keys[i]);
            if (currentNode.isMissingNode() || !currentNode.isObject()) {
                throw new RuntimeException("路径不存在或不是对象结构: " + keys[i]);
            }
        }

        // 找到最后一个节点所在的 ObjectNode
        if (currentNode instanceof ObjectNode) {
            ObjectNode parentNode = (ObjectNode) currentNode;
            String lastKey = keys[keys.length - 1];

            // 使用 valueToTree 自动处理各种数据类型 (String, Integer, Boolean, List, Map 等)
            parentNode.set(lastKey, yamlMapper.valueToTree(newValue));
        }
    }
}
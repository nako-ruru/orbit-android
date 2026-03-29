package com.orbit;

import android.Manifest;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.net.VpnService;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.app.AppOpsManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewCompat;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.connect_screen.mirror.MirrorMainActivity;
import com.connect_screen.mirror.State;
import com.connect_screen.mirror.SunshineService;
import com.connect_screen.mirror.TouchpadAccessibilityService;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pivovarit.function.ThrowingRunnable;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import aar.Aar;

public class MainActivity extends AppCompatActivity {
    private String mId;
    private WebView mWebView;

    public static MainActivity activity;

    private final ActivityResultLauncher<Intent> projectionLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        int resultCode = result.getResultCode();
        Intent data = result.getData();
        if (resultCode == RESULT_OK && data != null) {
            State.log("用户授予了投屏权限");
            if (SunshineService.instance == null) {
                Intent sunshineServiceIntent = new Intent(this, SunshineService.class);
                sunshineServiceIntent.putExtra("data", data);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(sunshineServiceIntent);
                } else {
                    startService(sunshineServiceIntent);
                }
                State.log("启动 SunshineService 服务");
            } else {
                MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                State.setMediaProjection(mediaProjectionManager.getMediaProjection(RESULT_OK, data));
                State.getMediaProjection().registerCallback(new MediaProjection.Callback() {
                    @Override
                    public void onStop() {
                        super.onStop();
                        State.log("MediaProjection onStop 回调");
                    }
                }, null);
                State.resumeJob();
            }
        } else {
            State.log("用户拒绝了投屏权限");
//                refresh();
            State.resumeJob();
        }
    });

    private final ActivityResultLauncher<Intent> safLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        int resultCode = result.getResultCode();
        if (resultCode == RESULT_OK) {
            Intent data = result.getData();
            Uri uri = data.getData();
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            String uris = "[]";
            try {
                uris = prefs.getString("dir_uris", "[]");
            } catch (RuntimeException e) {
                Log.e("MainActivity", "asfLauncher", e);
            }
            try {
                Set<MountPoint> mountPoints = new ObjectMapper().readValue(uris,   new TypeReference<LinkedHashSet<MountPoint>>() {});
                if(!mountPoints.contains(MountPoint.createMount("0", uri.toString()))) {
                    Set<String> rootIds = mountPoints.stream()
                            .map(MountPoint::getRootId)
                            .collect(Collectors.toSet());
                    String newRootId;
                    do {
                        newRootId = NanoIdUtils.randomNanoId(new Random(), "0123456789abcdefghijklmnopqrstuvwxyz".toCharArray(), 6);
                    } while (rootIds.contains(newRootId));
                    mountPoints.add(MountPoint.createMount(newRootId, uri.toString()));
                    prefs.edit().putString("dir_uris", new ObjectMapper().writeValueAsString(mountPoints)).apply();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    });

    private final ActivityResultLauncher<Intent> commonLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),    result -> {
        }    );

    private final ActivityResultLauncher<String[]> runtimePermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            }    );

    public MainActivity() {
        super();
        Log.i("LIFECYCLE", MainActivity.class.getName());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i("LIFECYCLE", "GoWebViewActivity.onCreate");

        super.onCreate(savedInstanceState);

        Aar.registerMoonlightProvider(new AndroidMoonlightProvider(this));

        activity = this;

        mId = "500";
        mWebView = new WebView(this);
        AndroidWebViewProvider.bind(mId, mWebView, this::finish);

        WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                // 映射 /assets/ 到 APK assets 目录
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public String callGo(String name, String args) {
                return Aar.callBinding(mId, name, args);
            }
            // 方法二：专门处理权限的方法
            @JavascriptInterface
            public void requestPermission(String type) {
                // 直接在这里写跳转逻辑，或者调用你写的 handlePermissionJump(type)
                handlePermissionRequest(type);
            }
        }, "_android_bridge");
        String jsInit = getIntent().getStringExtra("JS_INIT");
        if(jsInit != null && !jsInit.isBlank()) {
            WebViewCompat.addDocumentStartJavaScript(mWebView, jsInit, Collections.singleton("*"));
        }
        String bindings = getIntent().getStringExtra("BINDINGS");
        if(bindings != null && !bindings.isBlank()) {
            WebViewCompat.addDocumentStartJavaScript(mWebView, injectBindings(bindings), Collections.singleton("*"));
        }
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                syncAllPermissionsToWeb();
            }
            @Override
            public WebResourceResponse shouldInterceptRequest(
                    WebView view,
                    WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }
        });
        mWebView.loadUrl(getIntent().getStringExtra("URL"));
        setContentView(mWebView);
    }

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private final Runnable mPermissionAction = () -> {
        if(!isFinishing() && !isDestroyed()) {
            if(hasWindowFocus()) {
                runOnUiThread(() -> {
                    // 检查 SunshineService 是否已经在运行，如果没有运行才启动
                    Log.i("GoWebViewActivity", "SunshineService.instance = " + SunshineService.instance);
                    if (SunshineService.instance == null) {
                        startMediaProjectionService();
                    } else {
                        State.log("SunshineService 服务已在运行");
                    }
                });
            }
            syncAllPermissionsToWeb();
        }
    };

    @Override
    protected void onPostResume() {
        super.onPostResume();
        mHandler.removeCallbacks(mPermissionAction);
        mHandler.postDelayed(mPermissionAction, 1 * 1000);
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                HiddenApiBypass.addHiddenApiExemptions("");
                android.util.Log.i("MainActivity", "成功添加隐藏API豁免");
            } catch (Exception e) {
                android.util.Log.e("MainActivity", "添加隐藏API豁免失败: " + e.getMessage());
            }
        }
    }

    public static String injectBindings(String names) {
        return Arrays.stream(names.split(","))
                .map(String::trim)
                .map(MainActivity::injectBinding)
                .collect(Collectors.joining());
    }
    private static String injectBinding(String name) {
        return String.format("window.%s = (...args) => _android_bridge.callGo('%s', JSON.stringify(args));", name, name);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 彻底销毁，防止 Activity 销毁后任务还跑出来导致的内存泄漏
        mHandler.removeCallbacksAndMessages(null);
        AndroidWebViewProvider.unbind(mId);
        Aar.notifyWebviewExit(mId);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MirrorMainActivity.REQUEST_RECORD_AUDIO_PERMISSION) {
            State.resumeJob();
        } else {
            State.log("未知权限请求代码: " + requestCode);
        }
    }

    public void startMediaProjectionService() {
        MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) this.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mediaProjectionManager != null) {
            TouchpadAccessibilityService.grantPermissionByClick(activity);
            Intent captureIntent = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                captureIntent = mediaProjectionManager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay());
            } else {
                captureIntent = mediaProjectionManager.createScreenCaptureIntent();
            }
            projectionLauncher.launch(captureIntent);
        } else {
            throw new RuntimeException("无法获取 MediaProjectionManager 服务");
        }
    }
    @Override
    public void finish() {
        // 强制打印当前的调用栈，不要管系统怎么调的
        Log.e("TraceFinish", "MainActivity finish 被调用", new Throwable());
        super.finish();
    }

    /**
     * 核心权限跳转逻辑
     * @param type 由 JS 传过来的权限标识符
     */
    private void handlePermissionRequest(String type) {
        switch (type) {
            case "mic":
                // 运行时权限不能用 Intent 跳转设置页
                // 必须调用你在 onCreate 中注册的那个 String[] 类型的启动器
                if (runtimePermissionLauncher != null) {
                    runtimePermissionLauncher.launch(new String[]{
                            "android.permission.RECORD_AUDIO"
                    });
                } else {
                    Log.e("Permission", "runtimePermissionLauncher 未初始化！");
                }
                return;
            case "projection":
                // 投屏权限：注意这不会跳转设置页，而是直接弹窗
                MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                if (projectionManager != null) {
                    // 注意：这个结果必须通过特定的 launcher 接收并保存 Intent
                    projectionLauncher.launch(projectionManager.createScreenCaptureIntent());
                }
                return;
            case "power":
                requestIgnoreBatteryOptimizations();
                return;
            case "files":
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                safLauncher.launch(intent);
                return;
        }

        Intent intent = null;
        switch (type) {
            case "accessibility":
                // 跳转到无障碍设置主页（目前 Android 无法直接跳转到特定应用的无障碍开关，除非是系统应用）
                intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                break;
            case "overlay":
                // 跳转到悬浮窗权限设置页（精准定位到本 App）
                intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                break;
            case "alarm":
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // 同理，如果精确闹钟也报找不到符号，请直接用字符串：
                    intent = new Intent("android.settings.REQUEST_SCHEDULE_EXACT_ALARM", Uri.parse("package:" + getPackageName()));
                }
                break;
            case "autostart":
                // 跳转到详情页
                intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                break;
            case "notification":
                if (android.os.Build.VERSION.SDK_INT >= 33) { // Android 13+
                    // 这里 launch 接收的是 String[]，现在类型匹配了！
                    runtimePermissionLauncher.launch(new String[]{"android.permission.POST_NOTIFICATIONS"});
                    return;
                } else {
                    // 低版本跳设置页，这里需要用 Intent 类型的 launcher（比如 commonLauncher）
                    intent = new Intent("android.settings.APP_NOTIFICATION_SETTINGS");
                    intent.putExtra("android.provider.extra.APP_PACKAGE", getPackageName());
                }
                break;
            case "vpn":
                // VPN 比较特殊，系统直接返回一个配置 Intent
                intent = VpnService.prepare(this);
                break;
            case "popup":
                intent = launchBackgroundStartSettings(this);
                break;
        }
        try {
            // 执行跳转
            if (intent != null) {
                commonLauncher.launch(intent);
            }
        } catch (Exception e) {
            Log.e("PermissionJump", "跳转失败: " + type, e);
            // 如果精准跳转失败（部分国产 ROM 阉割了路径），尝试降级跳转到设置主页
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    // 1. 录音/麦克风
    private boolean checkMicStatus() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    // 2. 无障碍服务 (注意：需要填入你自己的服务类名)
    private boolean checkAccStatus() {
        return TouchpadAccessibilityService.isAccessibilityServiceEnabled(this);
    }

    // 3. 悬浮窗
    private boolean checkOverlayStatus() {
        return Settings.canDrawOverlays(this);
    }
    public Intent launchBackgroundStartSettings(Context context) {
        String brand = android.os.Build.MANUFACTURER.toLowerCase();
        Intent intent = new Intent();
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            if (brand.contains("xiaomi")) {
                // 小米/HyperOS：直接跳到“后台弹出界面”所在的权限管理页
                intent.setAction("miui.intent.action.APP_PERM_EDITOR");
                intent.setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity");
                intent.putExtra("extra_pkgname", context.getPackageName());

            } else if (brand.contains("vivo")) {
                // vivo：跳转到“权限管理”详情页
                intent.setClassName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity");
                intent.putExtra("packagename", context.getPackageName());

            } else if (brand.contains("oppo") || brand.contains("realme")) {
                // OPPO：跳转到权限详情页
                intent.setClassName("com.coloros.safecenter", "com.coloros.safecenter.permission.PermissionManagerActivity");
                // 部分版本可能需要跳转到应用详情页手动点击
                if (context.getPackageManager().resolveActivity(intent, 0) == null) {
                    intent = getAppDetailsIntent(context);
                }

            } else if (brand.contains("huawei") || brand.contains("honor")) {
                // 华为：跳转到“应用启动管理”（包含自启动/后台运行）
                intent.setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity");

            } else {
                // 兜底：跳转到通用的“应用详情页”
                intent = getAppDetailsIntent(context);
            }
            return intent;
        } catch (Exception e) {
            // 万一私有 Intent 失效，跳转应用详情页作为保底
            return (getAppDetailsIntent(context));
        }
    }

    private Intent getAppDetailsIntent(Context context) {
        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(android.net.Uri.parse("package:" + context.getPackageName()));
        return intent;
    }

    /**
     * 判断“后台弹出界面”权限是否开启
     * 适用于：小米(MIUI/HyperOS)、vivo、OPPO
     */
    private boolean isBackgroundStartAllowed() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) return true;

        AppOpsManager ops = (AppOpsManager) this.getSystemService(Context.APP_OPS_SERVICE);
        try {
            Method method = AppOpsManager.class.getMethod("checkOp", int.class, int.class, String.class);

            // 关键：不同厂商定义的 OpCode 不同
            int opCode = -1;
            String brand = Build.MANUFACTURER.toLowerCase();

            if (brand.contains("xiaomi")) {
                opCode = 10021; // 小米：OP_BACKGROUND_START_ACTIVITY
            } else if (brand.contains("vivo")) {
                opCode = 10021; // vivo：同样使用 10021 (显示后台浮窗)
            } else if (brand.contains("oppo")) {
                opCode = 10035; // OPPO：部分版本使用 10035
            }

            if (opCode != -1) {
                int mode = (int) method.invoke(ops, opCode, Binder.getCallingUid(), this.getPackageName());
                return mode == AppOpsManager.MODE_ALLOWED;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 华为/其他机型：如果没有特定的 OpCode，通常认为“自启动”开启即代表允许
        return true;
    }

    public boolean isMediaProjectionPermissionGranted(Context context) {
        if (Build.VERSION.SDK_INT >= 34) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    // 4. VPN (如果返回 null 说明已经通过了系统的 prepare 检查)
    private boolean checkVpnStatus() {
        return android.net.VpnService.prepare(this) == null;
    }

    // 5. 所有文件访问 (Android 11+)
    private boolean checkFilesStatus() {
        return false;
    }

    // 6. 精确闹钟 (Android 12+)
    private boolean checkAlarmStatus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            return am != null && am.canScheduleExactAlarms();
        }
        return true;
    }

    // 7. 通知监听 (此处通常指通知栏读写权限，如需检查动态权限用下面的方法)
    private boolean checkNotifStatus() {
        if (Build.VERSION.SDK_INT >= 33) {
            return ContextCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private boolean isBatteryUnrestricted() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            // 返回 true 表示已经是“无限制/不优化”状态
            return pm.isIgnoringBatteryOptimizations(getPackageName());
        }
        return false;
    }

    private boolean checkAutoStartStatus() {
        try {
            AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            // OP_RUN_IN_BACKGROUND 通常与自启动/后台运行逻辑相关
            int mode = appOps.checkOpNoThrow("android:run_in_background", android.os.Process.myUid(), getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    private void syncAllPermissionsToWeb() {
        runOnUiThread(ThrowingRunnable.sneaky(() -> {
            // 1. 读取权限结构 JSON5（支持注释）
            String structureJson5 = loadJSONFromAsset("permission_definitions.json5");
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
            mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
            mapper.configure(JsonReadFeature.ALLOW_TRAILING_COMMA.mappedFeature(), true);
            mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
            // 读取 JSON 文件成树结构
            JsonNode rootNode = mapper.readValue(structureJson5, JsonNode.class);

            // 确保 groups 是数组
            ArrayNode groups = (ArrayNode) rootNode.get("groups");

            // 2. 获取当前所有权限状态
            Map<String, Boolean> statusMap = getAllPermissionsStatus();

            // 3. 注入权限状态到 groups
            for (JsonNode groupNode : groups) {
                ArrayNode permissions = (ArrayNode) groupNode.get("permissions");

                for (JsonNode permNode : permissions) {
                    String permId = permNode.get("id").asText();
                    boolean granted = statusMap.getOrDefault(permId, false);

                    // 这里 JsonNode 是不可变的，需要转成 ObjectNode 才能 put
                    if (permNode instanceof ObjectNode) {
                        ((ObjectNode) permNode).put("granted", granted);
                    }
                }
            }

            // 4. 构建最终数据
            ObjectNode finalData = mapper.createObjectNode();
            finalData.set("groups", groups);

            // 5. 转成 JSON 字符串并调用 JS
            String finalJson = mapper.writeValueAsString(finalData);
            String jsCode = "document.getElementById('permissionFrame').contentWindow.updatePermissionUI(" + finalJson + ")";

            mWebView.evaluateJavascript(jsCode, null);
        }));
    }

    /**
     * 获取所有权限的当前状态
     */
    private Map<String, Boolean> getAllPermissionsStatus() {
        Map<String, Boolean> status = new HashMap<>();

        // 核心投屏功能组
        status.put("mic", checkMicStatus());                           // 录音/麦克风
        status.put("projection", isMediaProjectionPermissionGranted(this)); // 投屏权限
        status.put("vpn", checkVpnStatus());                           // VPN转发
        status.put("accessibility", checkAccStatus());                 // 无障碍服务

        // 界面交互与监控组
        status.put("overlay", checkOverlayStatus());                   // 悬浮窗权限
        status.put("notification", checkNotifStatus());                // 通知监听
        status.put("files", checkFilesStatus());                       // 所有文件访问

        // 进程保活与自动重启组
        status.put("autostart", checkAutoStartStatus());               // 自启动权限
        status.put("popup", isBackgroundStartAllowed());               // 后台弹出界面
        status.put("power", isBatteryUnrestricted());                  // 省电策略白名单
        status.put("alarm", checkAlarmStatus());                       // 精确闹钟与重启

        return status;
    }


    private void requestIgnoreBatteryOptimizations() {
        // 1. 获取电源管理器
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);

        if (powerManager != null) {
            // 2. 检查当前 App 是否已经在白名单（无限制）中
            if (!powerManager.isIgnoringBatteryOptimizations(getPackageName())) {

                // 3. 如果不在白名单，则构建 Intent 准备弹出系统对话框
                // 注意：ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 必须配合 "package:包名" 数据
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));

                // 4. 启动跳转
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    // 部分极少数机型可能不支持直接弹出，跳转到列表页作为兜底
                    Intent fallbackIntent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                    startActivity(fallbackIntent);
                }
            }
        }
    }

    // 权限定义文件路径
    private static final String PERMISSION_DEFINITIONS_FILE = "permission_definitions.json";

    // 多语言文件路径模板
    private static final String I18N_FILE_TEMPLATE = "permission_i18n_%s.json";


    /**
     * 从 assets 读取 JSON 文件
     */
    private String loadJSONFromAsset(String filename) throws IOException {
        InputStream is = this.getAssets().open(filename);
        int size = is.available();
        byte[] buffer = new byte[size];
        is.read(buffer);
        is.close();
        return new String(buffer, StandardCharsets.UTF_8);
    }

}

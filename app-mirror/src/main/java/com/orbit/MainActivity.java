package com.orbit;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.AlarmManager;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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
import android.view.accessibility.AccessibilityManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewCompat;

import com.connect_screen.mirror.MirrorMainActivity;
import com.connect_screen.mirror.State;
import com.connect_screen.mirror.TouchpadAccessibilityService;
import com.connect_screen.mirror.shizuku.ShizukuUtils;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.json.JSONException;
import org.json.JSONObject;
import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import aar.Aar;
import rikka.shizuku.Shizuku;
import xyz.kumaraswamy.autostart.Autostart;

public class MainActivity extends androidx.activity.ComponentActivity {
    private String mId;
    private WebView mWebView;

    public static Reference<MainActivity> activity;

    private final ActivityResultLauncher<Intent> safLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        int resultCode = result.getResultCode();
        if (resultCode == RESULT_OK && result.getData() != null) {
            Uri uri = result.getData().getData();
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            try {
                String uris = prefs.getString("dir_uris", "[]");
                Set<MountPoint> mountPoints = new ObjectMapper().readValue(uris, new TypeReference<LinkedHashSet<MountPoint>>() {});
                if (!mountPoints.contains(MountPoint.createMount("0", uri.toString()))) {
                    // 2. 获取当前文件夹的真实原始名称
                    DocumentFile doc = DocumentFile.fromTreeUri(this, uri);
                    String baseName = doc.getName() != null ? doc.getName() : "Folder";

                    // 3. 查重：看看目前已经存了多少个同名或带序号的 Root 了
                    Set<String> existingIds = mountPoints.stream().map(MountPoint::getRootId).collect(Collectors.toSet());
                    String uniqueRootId = baseName;
                    int seq = 1;
                    while (existingIds.contains(uniqueRootId)) {
                        uniqueRootId = String.format("%s (%d)", baseName, seq++);
                    }

                    // 4. 把这个唯一的 "A" 或 "A (1)" 直接当做 rootId 存死
                    mountPoints.add(MountPoint.createMount(uniqueRootId, uri.toString()));
                    prefs.edit().putString("dir_uris", new ObjectMapper().writeValueAsString(mountPoints)).apply();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    });

    // 1. 定义 Shizuku 权限请求的回调监听器
    private final Shizuku.OnRequestPermissionResultListener shizukuPermissionListener = (requestCode, grantResult) -> {
        // 这里的 requestCode 就是你传进去的 200
        if (requestCode == 200) {
            // PackageManager.PERMISSION_GRANTED = 0，代表用户点了“允许”
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                System.out.println("[Shizuku] 用户已点击允许！立刻开始修改权限清单...");
                // 【核心核心】在这里执行你通过 Shizuku 修改本应用权限的逻辑！
                checkAndGrantNotificationPermission();

            } else {
                System.out.println("[Shizuku] 用户点击了拒绝！");
                // 可以通知前端 WebView：用户拒绝了，保持步骤 3 的高亮，提示用户必须允许
            }
        }
    };

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

        AutoUpdate.checkUpdate(this);
        Aar.registerMoonlightProvider(new AndroidMoonlightProvider(this));
        Aar.registerAuthProvider(new AndroidAuthProvider(this));
        activity = new WeakReference<>(this);

        mId = "500";
        mWebView = new WebView(this);
        AndroidWebViewProvider.bind(mId, mWebView, this::finish);

        WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                // 映射 /assets/ 到 APK assets 目录
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        mWebView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public String callGo(String name, String args) throws Exception {
                return Aar.callBinding(mId, name, args);
            }
            @JavascriptInterface
            public void requestPermission(String type) {
                handlePermissionRequest(type);
            }
            @JavascriptInterface
            public String fetchPermissions() throws IOException {
                return MainActivity.this.fetchPermissions();
            }
            @JavascriptInterface
            public void openExternalUrl(String url) {
                MainActivity.this.openBrowser(url);
            }
            @JavascriptInterface
            public String getShizukuStatus() throws JSONException {
                return MainActivity.this.getShizukuStatus();
            }
            @JavascriptInterface
            public void requestShizukuPermission() {
                MainActivity.this.requestShizukuPermission();
            }
            /**
             * 对应前端的：window._android_bridge.openShizuku()
             * 专属方法：直接打开 Shizuku
             */
            @android.webkit.JavascriptInterface
            public void openShizuku() {
                MainActivity.this.openShizuku();
            }
            /**
             * 对应前端的：window._android_bridge.openApp('moe.shizuku.privileged.api')
             * 通用方法：通过包名打开任意第三方应用（未来你可能用来打开 Rclone 或其他配套工具）
             */
            @android.webkit.JavascriptInterface
            public void openApp(String packageName) {
                MainActivity.this.openApp(packageName);
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
            public WebResourceResponse shouldInterceptRequest(
                    WebView view,
                    WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }
        });
        mWebView.loadUrl(getIntent().getStringExtra("URL"));
        mWebView.setWebContentsDebuggingEnabled(true);
        setContentView(mWebView);

        // 2. 在应用创建时，把监听器注册到 Shizuku 中
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener);
    }

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onNewIntent(Intent intent) {
        Log.i("MainActivity", "onNewIntent called!");
        super.onNewIntent(intent);
        // 1. 这一步很重要，更新当前的 Intent
        setIntent(intent);

        // 2. 拿到 code
        Uri uri = intent.getData();
        if (uri != null && "orbit".equals(uri.getScheme())) {
            // 直接调用你的 Provider 静态方法把 code 给 Go
            AndroidAuthProvider.handleResult(uri);
        }
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        mWebView.evaluateJavascript(""" 
            if (window.onAppResume) {
                onAppResume()
            }
        """, null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mWebView.evaluateJavascript("""
            if (window.onAppPause) {
                onAppPause()
            }
                """, null);
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
        String script = String.format("""
    window.%s =  (...args) => {
      let res = _android_bridge.callGo('%s', JSON.stringify(args));
      try {
                          // 如果是 JSON 字符串，说明是 Object 模式
                          return JSON.parse(res);
                      } catch (e) {
                          // 如果解析失败，说明是性能敏感的 Raw String 模式
                          return res;
                      }
    };
    """, name, name);
        return script;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 3. 必须在销毁时注销，否则会导致内存泄漏（死掉的 Activity 还被 Shizuku 引用着）
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener);
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

    // 真正执行修改权限清单的方法
    private void checkAndGrantNotificationPermission() {
        Handler handler = new Handler();
        handler.post(() -> {
            if (ShizukuUtils.hasPermission() && State.userService == null) {
                State.log("try start shizuku user service");
                State.bindUserService()
                        .thenRun(() -> {
                            mWebView.evaluateJavascript(""" 
                // onAppResume 会强制更新ui
                onAppResume();
        """, null);
                        });
            }
        });
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
        return isAccessibilitySettingsOn(this, TouchpadAccessibilityService.class);
    }

    public static boolean isAccessibilitySettingsOn(Context context, Class<?> serviceClass) {
        AccessibilityManager am = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null) {
            return false;
        }

        // 获取所有已启用的无障碍服务（此处传入 FEEDBACK_GENERIC，也可以传入 FEEDBACK_ALL_MASK）
        List<AccessibilityServiceInfo> enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC);

        // 拼接出你服务的完整组件名，格式为: "包名/全类名"
        String expectedComponentName = context.getPackageName() + "/" + serviceClass.getName();

        for (AccessibilityServiceInfo service : enabledServices) {
            if (expectedComponentName.equals(service.getId())) {
                return true;
            }
        }
        return false;
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
        if(DeviceUtils.isXiaomi()) {
            return Autostart.INSTANCE.getSafeState(this);
        }
        return false;
    }

    private String fetchPermissions() throws IOException {
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
        return finalJson;
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
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(getPackageName())) {

            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));

            try {
                // 使用统一的 Launcher 启动
                commonLauncher.launch(intent);
            } catch (Exception e) {
                Intent fallbackIntent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                commonLauncher.launch(fallbackIntent);
            }
        }
    }

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

    private void openBrowser(String downloadListUrl ) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(downloadListUrl));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // 确保在独立的任务栈中打开
        this.startActivity(intent);
    }

    private String getShizukuStatus() throws JSONException {
        boolean isInstalled = false;
        boolean isRunning = false;
        boolean isAuthorized = false;

        // 1. 判断是否安装了 Shizuku 应用
        try {
            // Shizuku 的官方包名是 "moe.shizuku.privileged.api"
            this.getPackageManager().getPackageInfo("moe.shizuku.privileged.api", 0);
            isInstalled = true;
        } catch (PackageManager.NameNotFoundException e) {
            isInstalled = false;
        }

        // 2. 判断 Shizuku 服务是否正在后台运行 (通过通信管道是否畅通来判断)
        if (isInstalled) {
            isRunning = Shizuku.pingBinder();
        }

        // 3. 判断当前 App 是否获得了 Shizuku 的授权
        if (isRunning) {
            isAuthorized = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        }

        // 4. 组装成前端需要的 JSON 结构
        JSONObject result = new JSONObject();
        result.put("installed", isInstalled);
        result.put("running", isRunning);
        result.put("authorized", isAuthorized);

        return result.toString();
    }

    private void requestShizukuPermission() {
        this.runOnUiThread(() -> {
            if (Shizuku.pingBinder()) {
                // 弹出我们之前讨论过的 “要允许 ORBIT 使用 Shizuku 吗？” 对话框
                Shizuku.requestPermission(200);
            }
        });
    }

    /**
     * 对应前端的：window._android_bridge.openShizuku()
     * 专属方法：直接打开 Shizuku
     */
    @android.webkit.JavascriptInterface
    public void openShizuku() {
        // 直接调用通用的打开方法，传入 Shizuku 的官方包名
        openApp("moe.shizuku.privileged.api");
    }

    /**
     * 对应前端的：window._android_bridge.openApp('moe.shizuku.privileged.api')
     * 通用方法：通过包名打开任意第三方应用（未来你可能用来打开 Rclone 或其他配套工具）
     */
    @android.webkit.JavascriptInterface
    public void openApp(String packageName) {
        if (this == null || packageName == null || packageName.isEmpty()) {
            return;
        }

        try {
            PackageManager packageManager = this.getPackageManager();
            // 1. 获取该应用启动的入口 Intent (Launch Intent)
            Intent intent = packageManager.getLaunchIntentForPackage(packageName);

            if (intent != null) {
                // 2. 加上必备标记，确保在 WebView 的 Context 或非 Activity 容器中也能安全拉起
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                this.startActivity(intent);
            } else {
                // 如果找不到入口，说明应用可能被禁用了，或者是个纯后台没有界面的服务（Shizuku 肯定有界面，所以理论上不会走这里）
                System.out.println("[Bridge] 找不到该应用的启动入口: " + packageName);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

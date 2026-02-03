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
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.webkit.WebViewCompat;

import com.connect_screen.mirror.MirrorMainActivity;
import com.connect_screen.mirror.State;
import com.connect_screen.mirror.SunshineService;
import com.connect_screen.mirror.TouchpadAccessibilityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pivovarit.function.ThrowingRunnable;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import aar.Aar;

public class MainActivity extends AppCompatActivity {
    private String mId;
    private WebView mWebView;

    public static MainActivity activity;

    ActivityResultLauncher<Intent> projectionLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
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

    ActivityResultLauncher<Intent> asfLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                int resultCode = result.getResultCode();
                if (resultCode == RESULT_OK) {
                    Intent data = result.getData();
                    Uri uri = data.getData();
                    getContentResolver().takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION |
                                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    );
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                    prefs.edit().putString("dir_uri", uri.toString()).apply();
                }
            }
    );

    // 通用跳转启动器（用于 VPN、悬浮窗等返回结果的检查）
    ActivityResultLauncher<Intent> commonLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
    result -> {
            // 用户从设置页回来了，通知 JS 刷新全部状态
            refreshPermissionStatusToJs();
        }
    );

    // 特定的 VPN 启动器（如果需要特殊处理结果）
    ActivityResultLauncher<Intent> vpnLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
    result -> {
            if (result.getResultCode() == RESULT_OK) {
                // 可以在这里保存 VPN 数据
            }
            refreshPermissionStatusToJs();
        }
    );

    ActivityResultLauncher<String[]>    runtimePermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            result -> {
                // 结果是一个 Map<String, Boolean>，处理完通知 JS
                refreshPermissionStatusToJs();
            }
    );

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
        String bindings = getIntent().getStringExtra("BINDINGS");
        if(bindings != null && !bindings.isBlank()) {
            WebViewCompat.addDocumentStartJavaScript(mWebView, injectBindings(bindings), Collections.singleton("*"));
        }
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                syncAllPermissionsToWeb();
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

                    {
                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
//            asfLauncher.launch(intent);
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
     * 核心方法：体检所有权限，并将 JSON 结果推送到网页
     */
    public void refreshPermissionStatusToJs() {
        // 必须在 UI 线程执行 evaluateJavascript
        runOnUiThread(() -> {
            try {
                Map<String, Object> status = new HashMap<>();
                String pkg = getPackageName();

                // 1. 运行时权限 (麦克风)
                status.put("mic", ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED);

                // 2. VPN (返回 null 表示已授权)
                status.put("vpn", VpnService.prepare(this) == null);

                // 3. 悬浮窗
                status.put("overlay", Settings.canDrawOverlays(this));

                // 4. 所有文件访问 (Android 11+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    status.put("files", Environment.isExternalStorageManager());
                } else {
                    status.put("files", ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            == PackageManager.PERMISSION_GRANTED);
                }

                // 5. 精确闹钟 (Android 12+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
                    status.put("alarm", am != null && am.canScheduleExactAlarms());
                } else {
                    status.put("alarm", true);
                }

                // 6. 忽略电池优化 (白名单)
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                status.put("ignoreBattery", pm != null && pm.isIgnoringBatteryOptimizations(pkg));

                // 7. 无障碍服务 (注意：这里需要传入你自己的 Service 类名)
                status.put("accessibility", TouchpadAccessibilityService.isAccessibilityServiceEnabled(this));

                // 8. 投屏权限 (注意：投屏通常是单次授权，这里建议检查你内存中是否存有令牌)
                // status.put("projection", mScreenCaptureIntent != null);

                // 转换成 JSON
                String json = new ObjectMapper().writeValueAsString(status);

                // 执行 JS 回调：确保你的 HTML 里有 updatePermissionUI 这个 function
                String jsCode = String.format("if(window.updatePermissionUI){ window.updatePermissionUI(%s); }", json);
                mWebView.evaluateJavascript(jsCode, null);

            } catch (Exception e) {
                Log.e("PermissionSync", "同步状态到 JS 失败", e);
            }
        });
    }

    private Map<String, Boolean> getAllPermissionsStatus() {
        Map<String, Boolean> status = new HashMap<>();
        String pkg = getPackageName();

        // 1. VPN
        status.put("vpn", VpnService.prepare(this) == null);

        // 2. 悬浮窗
        status.put("overlay", Settings.canDrawOverlays(this));

        // 3. 所有文件
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            status.put("files", Environment.isExternalStorageManager());
        } else {
            status.put("files", ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
        }

        // 4. 精确闹钟
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            status.put("alarm", am != null && am.canScheduleExactAlarms());
        } else {
            status.put("alarm", true);
        }

        // 5. 忽略电池优化
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        status.put("ignoreBattery", pm != null && pm.isIgnoringBatteryOptimizations(pkg));

        // 6. 无障碍 (需要替换为你自己的 AccessibilityService 类名)
        status.put("accessibility", TouchpadAccessibilityService.isAccessibilityServiceEnabled(this));

        return status;
    }

    /**
     * 核心权限跳转逻辑
     * @param type 由 JS 传过来的权限标识符
     */
    private void handlePermissionRequest(String type) {
        try {
            Intent intent = null;
            String packageName = getPackageName();

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
                case "vpn":
                    // VPN 比较特殊，系统直接返回一个配置 Intent
                    intent = VpnService.prepare(this);
                    if (intent != null) {
                        vpnLauncher.launch(intent);
                    } else {
                        // 已授权，刷新 UI 即可
                        refreshPermissionStatusToJs();
                    }
                    return;

                case "accessibility":
                    // 跳转到无障碍设置主页（目前 Android 无法直接跳转到特定应用的无障碍开关，除非是系统应用）
                    intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    break;

                case "overlay":
                    // 跳转到悬浮窗权限设置页（精准定位到本 App）
                    intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + packageName));
                    break;

                case "files":
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        // 尝试精准跳转
                        intent = new Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_CONFIRMATION");
                        intent.setData(Uri.parse("package:" + packageName));

                        // 部分系统需要这个分类才能识别精准路径
                        intent.addCategory(Intent.CATEGORY_DEFAULT);

                        try {
                            commonLauncher.launch(intent);
                        } catch (Exception e) {
                            // 如果精准页崩了或不支持，跳转到总列表页（这是最后的兜底）
                            Intent fallback = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                            commonLauncher.launch(fallback);
                        }
                    }
                    return;

                case "alarm":
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        // 同理，如果精确闹钟也报找不到符号，请直接用字符串：
                        intent = new Intent("android.settings.REQUEST_SCHEDULE_EXACT_ALARM",
                                Uri.parse("package:" + getPackageName()));
                    }
                    break;

                case "ignoreBattery":
                    // 跳转到“忽略电池优化”设置页（白名单）
                    intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:" + packageName));
                    break;

                case "usageStats":
                    // 跳转到“查看应用使用情况”权限页（有时控制端需要查询当前活动应用）
                    intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                    break;

                case "notification":
                    if (android.os.Build.VERSION.SDK_INT >= 33) { // Android 13+
                        // 这里 launch 接收的是 String[]，现在类型匹配了！
                        runtimePermissionLauncher.launch(new String[]{"android.permission.POST_NOTIFICATIONS"});
                    } else {
                        // 低版本跳设置页，这里需要用 Intent 类型的 launcher（比如 commonLauncher）
                        intent = new Intent("android.settings.APP_NOTIFICATION_SETTINGS");
                        intent.putExtra("android.provider.extra.APP_PACKAGE", getPackageName());
                        commonLauncher.launch(intent);
                    }
                    break;

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
            }

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

    // 4. VPN (如果返回 null 说明已经通过了系统的 prepare 检查)
    private boolean checkVpnStatus() {
        return android.net.VpnService.prepare(this) == null;
    }

    // 5. 所有文件访问 (Android 11+)
    private boolean checkFilesStatus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
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

    // 8. 设备管理员
    private boolean checkAdminStatus() {
        return true;
//        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
//        ComponentName adminName = new ComponentName(this, MyDeviceAdminReceiver.class);
//        return dpm != null && dpm.isAdminActive(adminName);
    }

    private boolean isBatteryUnrestricted() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            // 返回 true 表示已经是“无限制/不优化”状态
            return pm.isIgnoringBatteryOptimizations(getPackageName());
        }
        return false;
    }

    public void syncAllPermissionsToWeb() {
        runOnUiThread(ThrowingRunnable.sneaky(() -> {
            Map<String, Boolean> status = new HashMap<>();

            // 这些 Key 必须与 JS 中的 id="p-xxx" 后缀严格对应
            status.put("mic", checkMicStatus());               // 对应 id="p-mic"
            status.put("accessibility", checkAccStatus());     // 对应 id="p-accessibility"
            status.put("overlay", checkOverlayStatus());       // 对应 id="p-overlay"
            status.put("vpn", checkVpnStatus());               // 对应 id="p-vpn"
            status.put("files", checkFilesStatus());           // 对应 id="p-files"
            status.put("alarm", checkAlarmStatus());           // 对应 id="p-alarm"
            status.put("notification", checkNotifStatus());    // 对应 id="p-notification"
            status.put("device_admin", checkAdminStatus());     // 对应 id="p-device_admin"
            status.put("power", isBatteryUnrestricted());
            String json = new ObjectMapper().writeValueAsString(status);
            // 调用 JS 方法。注意：JS 方法名必须是 updatePermissionUI
            mWebView.evaluateJavascript("if(window.updatePermissionUI){ window.updatePermissionUI(" + json + "); }", null);
        }));
    }

    public void goToXiaomiBatterySetting() {
        try {
            Intent intent = new Intent("miui.intent.action.OP_AUTO_START");
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            // 或者直接打开电池详情页
            // Intent intent = new Intent();
            // intent.setClassName("com.miui.securitycenter", "com.miui.powercenter.detail.PowerUsageDetailActivity");
            startActivity(intent);
        } catch (Exception e) {
            // 兜底：跳转到普通设置
            requestIgnoreBatteryOptimizations();
        }
    }

    public void requestIgnoreBatteryOptimizations() {
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
}

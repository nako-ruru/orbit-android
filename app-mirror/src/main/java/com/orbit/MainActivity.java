package com.orbit;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.webkit.WebViewCompat;

import com.connect_screen.mirror.MirrorMainActivity;
import com.connect_screen.mirror.State;
import com.connect_screen.mirror.SunshineService;
import com.connect_screen.mirror.TouchpadAccessibilityService;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;

import aar.Aar;

public class MainActivity extends AppCompatActivity {
    private String mId;
    private WebView mWebView;

    public static final int X = 555555;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i("LIFECYCLE", "GoWebViewActivity.onCreate");

        super.onCreate(savedInstanceState);

        Aar.registerMoonlightProvider(new AndroidMoonlightProvider(this));

        activity = this;

        // 检查 SunshineService 是否已经在运行，如果没有运行才启动
        Log.i("GoWebViewActivity", "SunshineService.instance" + SunshineService.instance);
        if (SunshineService.instance == null) {
            startMediaProjectionService();
        } else {
            State.log("SunshineService 服务已在运行");
        }

        {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
//            startActivityForResult(intent, X);
        }

        mId = "500";
        mWebView = new WebView(this);
        AndroidWebViewProvider.bind(mId, mWebView, this::finish);

        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public String callGo(String name, String args) {
                String v = Aar.callBinding(mId, name, args);
                return v;
            }
        }, "_android_bridge");

        WebViewCompat.addDocumentStartJavaScript(mWebView, injectBindings(getIntent().getStringExtra("BINDINGS")), Collections.singleton("*"));
        mWebView.loadUrl(getIntent().getStringExtra("URL"));
        setContentView(mWebView);
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
        AndroidWebViewProvider.unbind(mId);
        Aar.notifyWebviewExit(mId);
        SunshineService.instance = null;
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
            Intent captureIntent = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                captureIntent = mediaProjectionManager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay());
            } else {
                captureIntent = mediaProjectionManager.createScreenCaptureIntent();
            }
            projectionLauncher.launch(captureIntent);
            TouchpadAccessibilityService.grantPermissionByClick(activity);
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
}

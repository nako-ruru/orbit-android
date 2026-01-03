package com.orbit;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

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

public class GoWebViewActivity extends AppCompatActivity {
    private String mId;
    private WebView mWebView;

    public static GoWebViewActivity activity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Aar.registerMoonlightProvider(new AndroidMoonlightProvider(this));

        activity = this;

        // 检查 SunshineService 是否已经在运行，如果没有运行才启动
        if (SunshineService.instance == null) {
            startMediaProjectionService();
        } else {
            State.log("SunshineService 服务已在运行");
        }

        mId = getIntent().getStringExtra("ID");
        AndroidWebViewProvider.bind(mId, this);

        mWebView = new WebView(this);
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public String callGo(String name, String args) {
                return aar.Aar.callBinding(mId, name, args);
            }
        }, "_android_bridge");

        WebViewCompat.addDocumentStartJavaScript(mWebView, injectBindings(getIntent().getStringExtra("BINDINGS")), Collections.singleton("*"));
        setContentView(mWebView);
        mWebView.loadUrl(getIntent().getStringExtra("URL"));
    }

    /**
     * {@inheritDoc}
     *
     * Dispatch onPause() to fragments.
     */
    @Override
    protected void onPause() {
        super.onPause();
    }

    /**
     * {@inheritDoc}
     *
     * Dispatch onResume() to fragments.  Note that for better inter-operation
     * with older versions of the platform, at the point of this call the
     * fragments attached to the activity are <em>not</em> resumed.
     */
    @Override
    protected void onResume() {
        super.onResume();
    }

    private String injectBindings(String names) {
        return Arrays.stream(names.split(","))
                .map(String::trim)
                .map(this::injectBinding)
                .collect(Collectors.joining());
    }
    private String injectBinding(String name) {
        return String.format("window.%s = (...args) => _android_bridge.callGo('%s', JSON.stringify(args));", name, name);
    }

    public void detach(String name) {
        String js = String.format("delete windows['%s']", name);
        mWebView.evaluateJavascript(js, null);
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
    public WebView getWebView() { return mWebView; }

    @Override
    protected void onDestroy() {
        super.onDestroy();
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == MirrorMainActivity.REQUEST_CODE_MEDIA_PROJECTION) {
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
        }
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
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
            if (activity != null) {
                activity.startActivityForResult(captureIntent, MirrorMainActivity.REQUEST_CODE_MEDIA_PROJECTION);
                TouchpadAccessibilityService.grantPermissionByClick(activity);
            }
        } else {
            throw new RuntimeException("无法获取 MediaProjectionManager 服务");
        }
    }
}

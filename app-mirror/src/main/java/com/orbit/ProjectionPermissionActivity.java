package com.orbit;

import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.connect_screen.mirror.R;
import com.connect_screen.mirror.State;
import com.connect_screen.mirror.SunshineService;
import com.connect_screen.mirror.job.ExitAll;

public class ProjectionPermissionActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification);

        // 1. 强行把原本全屏的窗口砍成顶部悬浮条
        Window window = getWindow();
        if (window != null) {
            // 🚀 【核心新增】：FLAG_WATCH_OUTSIDE_TOUCH
            // 允许把点击漏给后面的应用（看电影不耽误），同时只要后面被点击了，当前 Activity 就会收到一个 OUTSIDE 信号
            window.addFlags(
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            );

            window.setGravity(Gravity.TOP);
            window.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT
            );
        }

        // 2. 【核心新增】：点击卡片周围的透明 Margin 区域，直接隐藏提示条
        View rootLayout = findViewById(R.id.root_layout);
        if (rootLayout != null) {
            rootLayout.setOnClickListener(v -> {
                State.log("用户点击了提示条内部的透明边缘，隐藏悬浮条");
                finish(); // 只关闭UI，SunshineService 依旧在后台欢快地投屏
            });
        }

        // 3. 绑定“断开”按钮（点击此按钮才会真正彻底杀死投屏服务）
        View btnDisconnect = findViewById(R.id.btn_disconnect);
        if (btnDisconnect != null) {
            btnDisconnect.setOnClickListener(v -> {
                State.log("用户点击断开按钮，彻底释放资源...");
                ExitAll.execute(this, true);
            });
        }

        // 4. 注册并启动权限申请（保持你原有的优秀链路）
        ActivityResultLauncher<Intent> launcher = registerForActivityResult(
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
                                    finish();
                                }
                            }, null);
                            State.resumeJob();
                        }
                    } else {
                        State.log("用户拒绝了投屏权限");
                        State.resumeJob();
                        finish();
                    }
                }
        );

        // 5. 触发系统投屏授权弹窗
        MediaProjectionManager mm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        Intent captureIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            MediaProjectionConfig config = MediaProjectionConfig.createConfigForDefaultDisplay();
            captureIntent = mm.createScreenCaptureIntent(config);
        } else {
            captureIntent = mm.createScreenCaptureIntent();
        }
        launcher.launch(captureIntent);
    }

    // 🚀 【核心新增】：捕获整个屏幕其它任意“空白区域”的点击
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // 当用户点击了当前 Activity 物理窗口之外的任何地方（比如下方的桌面或电影）
        if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
            State.log("用户点击了屏幕外部空白区域（忽略操作），隐藏悬浮条");
            finish(); // 功成身退：只关闭 UI 提示框，后台投屏不受丝毫影响
            return true;
        }
        return super.onTouchEvent(event);
    }
}
package com.orbit;

import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.connect_screen.mirror.R;
import com.connect_screen.mirror.State;
import com.connect_screen.mirror.SunshineService;

public class ProjectionPermissionActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. 立即注册并启动
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
        );

        MediaProjectionManager mm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        Intent captureIntent;
        // 判断是否是 Android 14 (API 34) 及以上版本
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // 核心点：通过 createConfigForDefaultDisplay() 隐式指定目标为“整个物理屏幕”
            MediaProjectionConfig config = MediaProjectionConfig.createConfigForDefaultDisplay();
            // 将全屏配置传入
            captureIntent = mm.createScreenCaptureIntent(config);
        } else {
            // Android 13 及以下：旧版 API 不支持配置，系统默认行为就是全屏
            captureIntent = mm.createScreenCaptureIntent();
        }
        launcher.launch(captureIntent);
/*
        setContentView(R.layout.activity_notification);

        // 关键控制：让这个 Activity 之外的空白区域，允许用户的触摸事件穿透到后面的看电影 App 中
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        );

        // 把窗口宽度强制拉满，靠顶对齐
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        lp.gravity = Gravity.TOP; // 贴在屏幕顶部
        getWindow().setAttributes(lp);

        // 点断开按钮时，停止投屏，finish
        findViewById(R.id.btn_disconnect).setOnClickListener(v -> {
        });
*/
    }
}
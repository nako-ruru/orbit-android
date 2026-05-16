package com.orbit;

import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

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
                    finish(); // 拿完钥匙赶紧闪人
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
    }
}
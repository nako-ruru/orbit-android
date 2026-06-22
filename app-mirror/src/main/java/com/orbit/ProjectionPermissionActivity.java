package com.orbit;

import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
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
            // 🛡️ 移除 FLAG_WATCH_OUTSIDE_TOUCH。现在用户乱点屏幕别的地方，悬浮条绝对不会被误隐藏！
            window.addFlags(
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            );

            window.setGravity(Gravity.TOP);
            window.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT
            );
        }

        // 2. 点击卡片自身周围的透明 Margin 区域，允许隐藏（属于有意图的局部点击）
        View rootLayout = findViewById(R.id.root_layout);
        if (rootLayout != null) {
            rootLayout.setOnClickListener(v -> {
                State.log("用户点击了提示条自身的透明边缘，隐藏悬浮条");
                finish();
            });
        }

        // 3. 【新增】绑定显式的“忽略”按钮
        View btnIgnore = findViewById(R.id.btn_ignore);
        if (btnIgnore != null) {
            btnIgnore.setOnClickListener(v -> {
                State.log("用户点击了明确的忽略按钮，隐藏悬浮条");
                finish(); // 仅仅关闭当前通知UI，后台投屏依然在安全运行
            });
        }

        // 4. 绑定“断开”按钮（点击此按钮才会真正彻底杀死投屏服务）
        View btnDisconnect = findViewById(R.id.btn_disconnect);
        if (btnDisconnect != null) {
            btnDisconnect.setOnClickListener(v -> {
                State.log("用户点击断开按钮，彻底释放资源...");
                ExitAll.execute(this, true);
            });
        }

        // 5. 注册并启动权限申请
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

        // 6. 触发系统投屏授权弹窗
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
}
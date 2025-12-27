package com.orbit;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.Log;

public class ProjectionPermissionActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 1. 弹出系统投屏授权框
        MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(manager.createScreenCaptureIntent(), 1000);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1000) {
            if (resultCode == RESULT_OK && data != null) {
                // 2. 授权成功，缓存数据并启动 Service
                AndroidSunshineProvider.cachedData = data;
                AndroidSunshineProvider.launchService(this, data);
            } else {
                Log.e("ProjectionPermissionActivity", "用户拒绝了授权");
            }
            // 3. 完成使命，关闭自己
            finish();
        }
    }
}
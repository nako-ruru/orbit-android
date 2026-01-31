package com.orbit;


import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

public class RestartHandlerActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 从 Intent 获取目标界面类名
        String targetClassName = getIntent().getStringExtra("next_activity");

        // 延迟 500ms 启动，确保旧进程已经彻底死透
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                Intent nextIntent = new Intent(this, Class.forName(targetClassName));
                nextIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(nextIntent);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            finish();
            // 任务完成，自杀中转进程
            android.os.Process.killProcess(android.os.Process.myPid());
        }, 100);
    }
}

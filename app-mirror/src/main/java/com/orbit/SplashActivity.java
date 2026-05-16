package com.orbit;

import android.app.Activity;
import android.os.Bundle;

import com.connect_screen.mirror.R;

public class SplashActivity  extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 1. 让 Activity 加载刚才写好的布局文件
        setContentView(R.layout.activity_splash);
    }


}

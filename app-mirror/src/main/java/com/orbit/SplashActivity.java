package com.orbit;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import java.io.IOException;
import java.io.InputStream;

import aar.Aar;

public class SplashActivity  extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new Thread(() -> {
            try {
                // 读取配置文件
                InputStream is =  this.getAssets().open("orbit.yml");
                byte[] data = new byte[is.available()];
                is.read(data);
                is.close();
                Aar.runOrbit(data);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).start();
        finish();
    }
}

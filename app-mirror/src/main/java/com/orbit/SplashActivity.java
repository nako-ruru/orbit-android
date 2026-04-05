package com.orbit;

import android.app.Activity;
import android.os.Bundle;

import com.pivovarit.function.ThrowingRunnable;

import java.io.InputStream;

import aar.Aar;

public class SplashActivity  extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new Thread(ThrowingRunnable.sneaky(() -> {
            // 读取配置文件
            InputStream is =  this.getAssets().open("orbit.yml");
            byte[] data = new byte[is.available()];
            is.read(data);
            is.close();
            Aar.runOrbit(data);
        })).start();
        finish();
    }
}

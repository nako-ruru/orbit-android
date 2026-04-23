package com.orbit;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import com.pivovarit.function.ThrowingRunnable;

import java.io.InputStream;

import aar.Aar;

public class SplashActivity  extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        finish();
    }


}

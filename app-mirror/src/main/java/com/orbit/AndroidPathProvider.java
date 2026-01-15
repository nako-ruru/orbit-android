package com.orbit;

import android.content.Context;

import aar.Aar;
import aar.PathProvider;

public class AndroidPathProvider implements PathProvider {

    private final Context context;

    public AndroidPathProvider(Context context) {
        this.context = context;
    }

    @Override
    public String getDeviceConfigDir() {
        return context.getFilesDir().getAbsolutePath();
    }

    @Override
    public String tempDir() {
        return context.getCacheDir().getAbsolutePath();
    }
}

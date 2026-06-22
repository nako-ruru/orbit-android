package com.orbit;

import android.content.Context;

import com.connect_screen.mirror.job.ExitAll;

import java.io.File;

import aar.StreamerProvider;
import aar.WebStreamerProvider;

public class AndroidStreamerProvider implements WebStreamerProvider {

    private final Context context;

    public AndroidStreamerProvider(Context context) {
        this.context = context;
    }

    @Override
    public String iceServerScriptPath() {
        String libraryPath = context.getApplicationInfo().nativeLibraryDir;
        return  new File(libraryPath, "libice_server_script.so").getAbsolutePath();
    }
}

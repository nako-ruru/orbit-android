package com.orbit;

import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;

import com.connect_screen.mirror.State;
import com.connect_screen.mirror.job.SunshineServer;

import aar.StreamerProvider;

public class AndroidSunshineProvider implements StreamerProvider {
    private final Context context;

    public AndroidSunshineProvider(Context context) {
        this.context = context;
    }

    @Override
    public void start() {
        MediaProjection mediaProjection = State.getMediaProjection();
        if(mediaProjection == null) {
            Intent intent = new Intent(context, ProjectionPermissionActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    @Override
    public boolean inputPin(String pin, String name) throws Exception {
        SunshineServer.submitPin(pin);
        return true;
    }
}

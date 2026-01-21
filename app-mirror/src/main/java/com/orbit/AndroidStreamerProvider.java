package com.orbit;

import android.content.Context;

import com.connect_screen.mirror.job.ExitAll;

import aar.StreamerProvider;

public class AndroidStreamerProvider implements StreamerProvider {

    private final Context context;

    public AndroidStreamerProvider(Context context) {
        this.context = context;
    }

    @Override
    public void onSessionTerminated() {
        ExitAll.execute(context, true);
    }
}

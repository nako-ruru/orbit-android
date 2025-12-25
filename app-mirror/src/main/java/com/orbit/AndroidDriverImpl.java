package com.orbit;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import aar.*;
import aar.Runnable;

public class AndroidDriverImpl implements AndroidDriver {
    private Context context;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final Map<String, WeakReference<GoWebViewActivity>> activityMap = new ConcurrentHashMap<>();

    public AndroidDriverImpl(Context context) {
        this.context = context;
    }

    public static void bind(String id, GoWebViewActivity act) {
        activityMap.put(id, new WeakReference<>(act));
    }
    public static void unbind(String id) {
        activityMap.remove(id);
    }

    @Override
    public void createAndStart(String id, String title, String url, String html, String jsInit, String bindings) {
        Intent i = new Intent(context, GoWebViewActivity.class);
        i.putExtra("ID", id);
        i.putExtra("URL", url);
        i.putExtra("HTML", html);
        i.putExtra("BINDINGS", bindings);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(i);
    }

    @Override
    public void postToUI(Runnable r) { mainHandler.post(r::run); }

    @Override
    public void evaluateJS(String id, String js) {
        mainHandler.post(() -> {
            WeakReference<GoWebViewActivity> ref = activityMap.get(id);
            if (ref != null && ref.get() != null) ref.get().getWebView().evaluateJavascript(js, null);
        });
    }

    @Override
    public void close(String id) {
        mainHandler.post(() -> {
            WeakReference<GoWebViewActivity> ref = activityMap.get(id);
            if (ref != null && ref.get() != null) ref.get().finish();
        });
    }
}

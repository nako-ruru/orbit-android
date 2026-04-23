package com.orbit;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebView;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import aar.*;
import aar.Runnable;

public class AndroidWebViewProvider implements WebViewProvider {
    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final Map<String, WebViweContext> activityMap = new ConcurrentHashMap<>();

    public AndroidWebViewProvider(Context context) {
        this.context = context;
    }

    public static void bind(String id, WebView webView, java.lang.Runnable callback) {
        activityMap.put(id, new WebViweContext(Objects.requireNonNull(webView), callback));
    }
    public static void unbind(String id) {
        activityMap.remove(id);
    }

    @Override
    public void createAndStart(String id, String title, String url, String html, String jsInit, String bindings) {
        Log.i("AndroidWebViewProvider", "createAndStart");
        mainHandler.post(() -> {
            Log.i("AndroidWebViewProvider", "createAndStart: mainHandler.post");
            Reference<Activity> activityRef = OrbitApplication.activity;
            if(id.equals("500")) {
                if(activityRef != null) {
                    Activity activity = activityRef.get();
                    if (activity != null) {
                        if(activity.getClass().getName().contains("SplashActivity")) {
                            activity.finish();
                        }
                    }
                }
            }
            Intent i;
            if(id.equals("500")) {
                Log.i("AndroidWebViewProvider", "createAndStart: 3");
                i = new Intent(context, MainActivity.class);
            } else {
                Log.i("AndroidWebViewProvider", "createAndStart: 4");
                i = new Intent(context, FileTransferActivity.class);
            }
            i.putExtra("ID", id);
            i.putExtra("URL", url);
            i.putExtra("HTML", html);
            i.putExtra("BINDINGS", bindings);
            i.putExtra("JS_INIT", jsInit);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
            Log.i("AndroidWebViewProvider", "createAndStart: 5");
        });
    }

    @Override
    public void dispatch(Runnable r) {
        mainHandler.post(r::run);
    }

    @Override
    public void evaluateJS(String id, String js) {
        mainHandler.post(() -> {
            WebViweContext ref = activityMap.get(id);
            if (ref != null && ref.webView.get() != null) {
                WebView webView = ref.webView.get();
                webView.evaluateJavascript(js, null);
            }
        });
    }

    @Override
    public void close(String id) {
        mainHandler.post(() -> {
            WebViweContext ref = activityMap.get(id);
            if (ref != null && ref.callback != null) {
                ref.callback.run();
            }
        });
    }

    private static class WebViweContext {
        public final Reference<WebView> webView;
        public final java.lang.Runnable callback;

        private WebViweContext(WebView webView, java.lang.Runnable callback) {
            this.webView = new WeakReference<>(webView);
            this.callback = callback;
        }

        public WebView get() {
            return webView.get();
        }
    }
}

package com.orbit;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;

import androidx.webkit.WebViewCompat;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import aar.*;
import aar.Runnable;

public class AndroidWebViewProvider implements WebViewProvider {
    private Context context;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final Map<String, WeakReference<GoWebViewActivity>> activityMap = new ConcurrentHashMap<>();

    public AndroidWebViewProvider(Context context) {
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
        mainHandler.post(() -> {
            WeakReference<GoWebViewActivity> ref = activityMap.get(id);
            if (ref != null && ref.get() != null) {
                GoWebViewActivity activity = ref.get();
                WebView webView = activity.getWebView();
                WebViewCompat.addDocumentStartJavaScript(webView, activity.injectBindings(bindings), Collections.singleton("*"));
                webView.loadUrl(url);
            }
        });
    }

    @Override
    public void postToUI(Runnable r) { mainHandler.post(r::run); }

    @Override
    public void evaluateJS(String id, String js) {
        mainHandler.post(() -> {
            WebView ref = getWebView(id);
            if (ref != null) {
                ref.evaluateJavascript(js, null);
            }
        });
    }

    @Override
    public void close(String id) {
        mainHandler.post(() -> {
            WeakReference<GoWebViewActivity> ref = activityMap.get(id);
            if (ref != null && ref.get() != null) {
                ref.get().finish();
            }
        });
    }

    private WebView getWebView(String id) {
        WeakReference<GoWebViewActivity> ref = activityMap.get(id);
        if (ref != null && ref.get() != null) {
            return ref.get().getWebView();
        }
        return null;
    }
}

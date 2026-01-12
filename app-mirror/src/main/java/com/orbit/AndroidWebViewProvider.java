package com.orbit;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;

import androidx.webkit.WebViewCompat;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import aar.*;
import aar.Runnable;

public class AndroidWebViewProvider implements WebViewProvider {
    private Context context;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
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
        mainHandler.post(() -> {
            WebViweContext ref = activityMap.get(id);
            if (ref != null && ref.webView.get() != null) {
                WebView webView = ref.webView.get();
                WebViewCompat.addDocumentStartJavaScript(webView, injectBindings(bindings), Collections.singleton("*"));
                webView.loadUrl(url);
            }
        });
    }

    @Override
    public void postToUI(Runnable r) { mainHandler.post(r::run); }

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

    public static String injectBindings(String names) {
        return Arrays.stream(names.split(","))
                .map(String::trim)
                .map(AndroidWebViewProvider::injectBinding)
                .collect(Collectors.joining());
    }
    private static String injectBinding(String name) {
        return String.format("window.%s = (...args) => _android_bridge.callGo('%s', JSON.stringify(args));", name, name);
    }

    private static class WebViweContext {
        public final WeakReference<WebView> webView;
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

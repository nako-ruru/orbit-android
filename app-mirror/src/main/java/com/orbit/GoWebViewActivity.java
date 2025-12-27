package com.orbit;

import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.webkit.WebViewCompat;

import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;

import aar.Aar;

public class GoWebViewActivity extends AppCompatActivity {
    private String mId;
    private WebView mWebView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mId = getIntent().getStringExtra("ID");
        AndroidWebViewProvider.bind(mId, this);

        mWebView = new WebView(this);
        mWebView.getSettings().setJavaScriptEnabled(true);
        // 注入桥梁
        mWebView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public String callGo(String name, String args) {
                return aar.Aar.callBinding(mId, name, args);
            }
        }, "_android_bridge");

        WebViewCompat.addDocumentStartJavaScript(mWebView, injectBindings(getIntent().getStringExtra("BINDINGS")), Collections.singleton("*"));
        setContentView(mWebView);
        mWebView.loadUrl(getIntent().getStringExtra("URL"));
    }

    private String injectBindings(String names) {
        return Arrays.stream(names.split(","))
                .map(String::trim)
                .map(this::injectBinding)
                .collect(Collectors.joining());
    }
    private String injectBinding(String name) {
        return String.format("window.%s = (...args) => _android_bridge.callGo('%s', JSON.stringify(args));", name, name);
    }

    public void detach(String name) {
        String js = String.format("delete windows['%s']", name);
        mWebView.evaluateJavascript(js, null);
    }

    public WebView getWebView() { return mWebView; }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AndroidWebViewProvider.unbind(mId);
        Aar.notifyWebviewExit(mId);
    }
}

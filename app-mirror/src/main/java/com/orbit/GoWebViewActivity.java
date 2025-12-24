package com.orbit;

import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

import aar.Aar;

public class GoWebViewActivity extends AppCompatActivity {
    private String mId;
    private WebView mWebView;
    private List<String> mBindings = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mId = getIntent().getStringExtra("ID");
        AndroidDriverImpl.bind(mId, this);

        mWebView = new WebView(this);
        mWebView.getSettings().setJavaScriptEnabled(true);
        // 注入桥梁
        mWebView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public String callGo(String name, String args) {
                return aar.Aar.callBinding(mId, name, args);
            }
        }, "_android_bridge");

        setContentView(mWebView);
        mWebView.loadUrl(getIntent().getStringExtra("URL"));
    }

    public void injectBinding(String name) {
        mBindings.add(name);
        String js = String.format("window.%s = (...args) => _android_bridge.callGo('%s', JSON.stringify(args));", name, name);
        mWebView.evaluateJavascript(js, null);
    }

    public WebView getWebView() { return mWebView; }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AndroidDriverImpl.unbind(mId);
        Aar.notifyWebviewExit(mId);
    }
}

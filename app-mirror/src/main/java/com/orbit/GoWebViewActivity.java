package com.orbit;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

import aar.Aar;

public class GoWebViewActivity extends AppCompatActivity {
    private String mId;
    private WebView mWebView;

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
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon)  {
                for(String name: getIntent().getStringExtra("BINDINGS").split(",")) {
                    Log.i("BINDINGS", name);
                    injectBinding(name.trim());
                }
            }
        });

        setContentView(mWebView);
        mWebView.loadUrl(getIntent().getStringExtra("URL"));
    }

    public void injectBinding(String name) {
        String js = String.format("window.%s = (...args) => _android_bridge.callGo('%s', JSON.stringify(args));", name, name);
        mWebView.evaluateJavascript(js, null);
    }

    public void detach(String name) {
        String js = String.format("delete windows['%s']", name);
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

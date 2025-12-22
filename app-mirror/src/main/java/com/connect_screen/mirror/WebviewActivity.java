package com.connect_screen.mirror;

import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;

public class WebviewActivity extends AppCompatActivity {

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);  // 启用 JS
        settings.setDomStorageEnabled(true);  // 支持 localStorage

        // 在 WebView 内打开页面，不跳转系统浏览器
        webView.setWebViewClient(new WebViewClient());

//        webView.loadUrl("file:///android_asset/index.html"); // 本地 HTML
        webView.loadUrl("https://www.163.com"); // 外部 URL
    }
}
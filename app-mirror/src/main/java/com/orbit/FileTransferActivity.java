package com.orbit;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewCompat;

import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;

import aar.Aar;

public class FileTransferActivity  extends AppCompatActivity {
    private String mId;
    private WebView mWebView;

    public FileTransferActivity() {
        super();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i("LIFECYCLE", "GoWebViewActivity.onCreate");

        super.onCreate(savedInstanceState);

        mId = "600";
        mWebView = new WebView(this);
        AndroidWebViewProvider.bind(mId, mWebView, this::finish);

        WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                // 映射 /assets/ 到 APK assets 目录
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(
                    WebView view,
                    WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }
            
        });
        mWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsConfirm(WebView view, String url, String message, final JsResult result) {
                new AlertDialog.Builder(view.getContext())
                        .setTitle(null) // 直接传 null，彻底干掉标题栏，连“提示”两个字都不要
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, (d, w) -> result.confirm())
                        .setNegativeButton(android.R.string.cancel, (d, w) -> result.cancel())
                        .setOnCancelListener(d -> result.cancel())
                        .show();
                return true;
            }
        });
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.getSettings().setDomStorageEnabled(true);
        mWebView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public String callGo(String name, String args) {
                try {
                    return Aar.callBinding(mId, name, args);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }, "_android_bridge_file_transfer");

        String jsInit = getIntent().getStringExtra("JS_INIT");
        if(jsInit != null && !jsInit.isBlank()) {
            WebViewCompat.addDocumentStartJavaScript(mWebView, jsInit, Collections.singleton("*"));
        }
        WebViewCompat.addDocumentStartJavaScript(mWebView, injectBindings(getIntent().getStringExtra("BINDINGS")), Collections.singleton("*"));
        mWebView.loadUrl(getIntent().getStringExtra("URL"));
        setContentView(mWebView);
    }

    public static String injectBindings(String names) {
        return Arrays.stream(names.split(","))
                .map(String::trim)
                .map(FileTransferActivity::injectBinding)
                .collect(Collectors.joining());
    }
    private static String injectBinding(String name) {
        String script = String.format("""
    window.%s = function(...args) {
        return new Promise(function(resolve, reject) {
            try {
                var res = _android_bridge_file_transfer.callGo('%s', JSON.stringify(args));
                  try {
                          // 如果是 JSON 字符串，说明是 Object 模式
                          res = JSON.parse(res);
                      } catch (e) {
                          // 如果解析失败，说明是性能敏感的 Raw String 模式
                      }
                resolve(res);
            } catch (e) {
                reject(e);
            }
        });
    };
    """, name, name);
        return script;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AndroidWebViewProvider.unbind(mId);
        Aar.notifyWebviewExit(mId);
    }
}

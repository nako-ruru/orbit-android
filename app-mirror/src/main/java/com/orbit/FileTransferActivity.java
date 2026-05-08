package com.orbit;

import android.os.Bundle;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

import androidx.appcompat.app.AppCompatActivity;
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

        mWebView.setWebChromeClient(new WebChromeClient());
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

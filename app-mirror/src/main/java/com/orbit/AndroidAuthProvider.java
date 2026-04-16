package com.orbit;

import android.content.Context;
import android.net.Uri;

import androidx.browser.customtabs.CustomTabsIntent;

import aar.AuthProvider;
import aar.LoginCallback;

public class AndroidAuthProvider implements AuthProvider {
    private final Context context;
    // 关键：存住 Go 传来的回调，等 onNewIntent 时用
    private static LoginCallback currentCallback;

    public AndroidAuthProvider(Context context) {
        this.context = context;
    }

    @Override
    public void doLogin(String authCodeURL, LoginCallback callback) {
        // 1. 保存回调
        currentCallback = callback;

        // 2. 拉起浏览器 (CCT)
        CustomTabsIntent intent = new CustomTabsIntent.Builder().build();
        // 注意：要在主线程执行
        intent.launchUrl(context, Uri.parse(authCodeURL));
    }

    @Override
    public String prepareLogin() throws Exception {
        return "myapp://auth";
    }

    // 供 MainActivity 调用的静态方法
    public static void handleResult(Uri uri) {
        if (uri != null && currentCallback != null) {
            String code = uri.getQueryParameter("code");
            String error = uri.getQueryParameter("error");

            if (code != null) {
                currentCallback.onResult(code, "");
            } else {
                currentCallback.onResult("", error != null ? error : "Login canceled");
            }
            currentCallback = null; // 用完释放
        }
    }
}
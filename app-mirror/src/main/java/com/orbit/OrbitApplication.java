package com.orbit;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;

public class OrbitApplication  extends Application {

    private boolean isInitialized;

    @Override
    public void onCreate() {
        Log.i("LIFECYCLE", "OrbitApplication.onCreate");
        super.onCreate();

        String id = "500";
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                Log.i("OrbitApplication", "onActivityCreated: " + activity);
                if (!isInitialized) {
                    MainActivity.test(activity, id);
                    isInitialized = true;
                }
            }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle bundle) {
            }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
            }
        });
    }
}
package com.orbit;

import android.content.Context;
import android.provider.Settings;

import aar.DeviceInfoProvider;

public class AndroidDeviceInfoProvider implements DeviceInfoProvider {

    private final Context context;

    public AndroidDeviceInfoProvider(Context context) {
        this.context = context;
    }

    @Override
    public String deviceName() {
        return Settings.Global.getString(
                context.getContentResolver(),
                Settings.Global.DEVICE_NAME
        );
    }
}

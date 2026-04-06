package com.orbit;


import android.os.Build;

public class DeviceUtils {

    /**
     * 获取手机厂商名称（小写格式）
     */
    public static String getManufacturer() {
        return Build.MANUFACTURER.toLowerCase();
    }

    /**
     * 华为设备检测（含荣耀子品牌）
     */
    public static boolean isHuawei() {
        return getManufacturer().contains("huawei") ||
                getManufacturer().contains("honor");
    }

    /**
     * 小米设备检测（含红米子品牌）
     */
    public static boolean isXiaomi() {
        return getManufacturer().contains("xiaomi") ||
                getManufacturer().contains("redmi");
    }

    /**
     * OPPO设备检测
     */
    public static boolean isOppo() {
        return getManufacturer().contains("oppo");
    }

    /**
     * vivo设备检测
     */
    public static boolean isVivo() {
        return getManufacturer().contains("vivo");
    }

    /**
     * 三星设备检测
     */
    public static boolean isSamsung() {
        return getManufacturer().contains("samsung");
    }

    /**
     * 获取带品牌图标的设备信息
     */
    public static String getBrandInfo() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;

        if (isHuawei()) {
            return "华为/HUAWEI " + model;
        } else if (isXiaomi()) {
            return "小米/Xiaomi " + model;
        } else if (isOppo()) {
            return "OPPO " + model;
        } else if (isVivo()) {
            return "vivo " + model;
        } else if (isSamsung()) {
            return "三星/SAMSUNG " + model;
        } else {
            return manufacturer + " " + model;
        }
    }
}
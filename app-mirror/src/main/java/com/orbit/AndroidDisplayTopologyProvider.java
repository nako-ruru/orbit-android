package com.orbit;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.view.Display;

import aar.DisplayTopology;
import aar.DisplayTopologyProvider;
import aar.MonitorInfo;
import aar.Resolution;

// 1. ✨ 直接实现 GoMobile 为你生成的 Java 接口
public class AndroidDisplayTopologyProvider implements DisplayTopologyProvider {

    private final Context context;

    public AndroidDisplayTopologyProvider(Context context) {
        this.context = context;
    }

    @Override
    public DisplayTopology getTopology() throws Exception {
        DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        Display[] displays = dm.getDisplays();

        // 1. 实例化 Go 对象的拓扑容器
        DisplayTopology topology = new DisplayTopology();

        for (Display display : displays) {
            MonitorInfo info = new MonitorInfo();
            info.setID(String.valueOf(display.getDisplayId()));
            info.setName(display.getName());
            info.setIsPrimary(display.getDisplayId() == Display.DEFAULT_DISPLAY);

            // 获取当前物理尺寸
            android.graphics.Point size = new android.graphics.Point();
            display.getRealSize(size);
            info.setCurrentWidth(size.x);
            info.setCurrentHeight(size.y);

            // 2. ✨ 枚举 Android 原生支持的 Modes 列表并利用辅助函数塞入 Go 对象
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                for (Display.Mode mode : display.getSupportedModes()) {
                    Resolution res = new Resolution();
                    res.setWidth(mode.getPhysicalWidth());
                    res.setHeight(mode.getPhysicalHeight());

                    // 调用刚才在 Go 里专门写给 Java 的单条追加方法
                    info.appendMode(res);
                }
            } else {
                // 极旧的 Android 设备兜底
                Resolution res = new Resolution();
                res.setWidth(size.x);
                res.setHeight(size.y);
                info.appendMode(res);
            }

            // 3. 🔥 调用 GoMobile 为你生成的追加方法，完美塞入拓扑
            topology.appendMonitor(info);
        }

        return topology;
    }

    @Override
    public MonitorInfo getActiveMonitor() throws Exception {
        // Android 直接无脑返回物理主屏幕（DEFAULT_DISPLAY）的信息即可！
        DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        Display defaultDisplay = dm.getDisplay(Display.DEFAULT_DISPLAY);

        MonitorInfo info = new MonitorInfo();
        info.setID(String.valueOf(defaultDisplay.getDisplayId()));
        info.setName("Android_Main_Screen");
        info.setIsPrimary(true);

        android.graphics.Point size = new android.graphics.Point();
        defaultDisplay.getRealSize(size);
        info.setCurrentWidth(size.x);
        info.setCurrentHeight(size.y);

        return info;
    }
}
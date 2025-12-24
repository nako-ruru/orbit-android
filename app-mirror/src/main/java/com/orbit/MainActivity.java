package com.orbit;

import android.app.Activity;
import android.os.Bundle;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        // 执行到这里，OrbitApplication 已经启动了 Go 协程
        finish(); // 任务完成，深藏功与名
    }
}
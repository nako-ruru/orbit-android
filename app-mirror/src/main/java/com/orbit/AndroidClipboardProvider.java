package com.orbit;


import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.core.content.FileProvider;

import com.connect_screen.mirror.State;
import com.connect_screen.mirror.shizuku.ShizukuUtils;
import com.pivovarit.function.ThrowingRunnable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import aar.ClipboardData;
import aar.ClipboardListener;
import aar.ClipboardProvider;

public class AndroidClipboardProvider implements ClipboardProvider {
    private final Context context;
    private final ClipboardManager cm;
    private final Handler mainHandler;
    private ClipboardListener listener;
    private boolean isSelfSetting = false;

    public AndroidClipboardProvider(Context context) {
        this.context = context.getApplicationContext();
        this.cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    // ================== 1. 写入逻辑：Go -> Android ==================
    @Override
    public void setData(ClipboardData data, String origin) throws Exception {
        mainHandler.post(ThrowingRunnable.sneaky(() -> {
            isSelfSetting = true;
            if (data.getText() != null && !data.getText().isEmpty()) {
                if(false && ShizukuUtils.hasPermission()) {
                    // 使用 Shizuku 执行 shell 命令，绕过后台应用无法访问剪贴板的限制
                    String cmd = "cmd clipboard set \"" + data.getText().replace("\"", "\\\"") + "\"";
                    // 伪代码：提交给你的 Shizuku 进程执行器
//                    ShizukuUtils.executeCommand(cmd);
                } else {
                    // 复制文本/HTML
                    ClipData clip = ClipData.newPlainText("text", data.getText());
//                    cm.setPrimaryClip(clip);
                }
            } else if (data.getImageData() != null && data.getImageData().length > 0) {
                if(ShizukuUtils.hasPermission()) {

                } else {
                    // 复制图片：先存入本地缓存，再通过 FileProvider 生成 Uri 塞入系统
                    File cacheFile = new File(context.getCacheDir(), "clip_sync.png");
                    try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
                        fos.write(data.getImageData());
                        fos.flush();
                    }

                    String authority = context.getPackageName() + ".provider"; // 这样动态获取最稳妥，结果就是 "com.orbit.provider"
                    Uri imageUri = FileProvider.getUriForFile(context, authority, cacheFile);

                    ClipData clip = ClipData.newUri(context.getContentResolver(), "image", imageUri);
                    cm.setPrimaryClip(clip);
                }
            }
        }));
    }


    @Override
    public void watch(ClipboardListener listener) throws Exception {
        this.listener = listener;
    }

    // ================== 2. 监听逻辑：Android -> Go ==================
    public void watch0() {
        mainHandler.post(() -> {
            if (cm == null) return;
            cm.addPrimaryClipChangedListener(() -> {
                if (isSelfSetting) {
                    isSelfSetting = false; // 拦截由自己写入引起的虚假改变事件
                    return;
                }

                // 开线程解析数据，避免阻塞 UI
                new Thread(ThrowingRunnable.sneaky(() -> {
                    ClipData clip = cm.getPrimaryClip();
                    if (clip == null || clip.getItemCount() == 0) return;

                    ClipData.Item item = clip.getItemAt(0);
                    ClipboardData goPack = new ClipboardData();

                    if (item.getText() != null) {
                        // 读取到了文本
                        goPack.setText(item.getText().toString());
                        listener.onClipboardChanged(goPack);
                    } else if (item.getUri() != null) {
                        // 读取到了 Uri（可能是图片）
                        Uri uri = item.getUri();
                        ContentResolver cr = context.getContentResolver();
                        String type = cr.getType(uri);

                        if (type != null && type.startsWith("image/")) {
                            try (InputStream is = cr.openInputStream(uri)) {
                                Bitmap bitmap = BitmapFactory.decodeStream(is);
                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);

                                goPack.setImageData(baos.toByteArray());
                                listener.onClipboardChanged(goPack);
                            }
                        }
                    }
                })).start();
            });
        });
    }
}
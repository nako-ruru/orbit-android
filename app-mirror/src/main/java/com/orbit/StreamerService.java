package com.orbit;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StreamerService{

    /**
     * 递归复制 Assets 文件夹到目标路径
     * @param context 上下文
     * @param assetPath Assets 内的文件夹路径 (例如 "my_data")
     * @param targetPath 目标物理路径 (例如 context.getFilesDir().getAbsolutePath() + "/my_data")
     */
    public static void copyAssetsFolder(Context context, String assetPath, File targetPath) throws IOException {
        AssetManager assetManager = context.getAssets();
        String[] assets = assetManager.list(assetPath);

        if (assets != null && assets.length > 0) {
            // 如果是文件夹，创建目标目录
            if (!targetPath.exists()) {
                targetPath.mkdirs();
            }

            // 递归子文件/文件夹
            for (String asset : assets) {
                String subAssetPath = assetPath.isEmpty() ? asset : assetPath + "/" + asset;
                File subDir = new File(targetPath,  asset);
                copyAssetsFolder(context, subAssetPath, subDir);
            }
        } else {
            // 如果是文件，直接复制
            copyFile(assetManager.open(assetPath), targetPath);
        }
    }

    private static void copyFile(InputStream in, File targetFile) throws IOException {
        try (OutputStream out = new FileOutputStream(targetFile)) {
            byte[] buffer = new byte[1024 * 8]; // 8KB 缓冲区
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } finally {
            if (in != null) in.close();
        }
    }
}


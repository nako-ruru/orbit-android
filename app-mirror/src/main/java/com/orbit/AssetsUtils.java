package com.orbit;

import android.content.Context;
import android.content.res.AssetManager;
import java.io.*;

public class AssetsUtils {

    /**
     * 递归复制 assets 目录到私有目录
     * @param context Context
     * @param assetDir assets 下的目录名，例如 "rclone_folder"
     * @param targetDir 目标目录，例如 getFilesDir() + "/rclone_folder"
     */
    public static void copyAssetsFolder(Context context, String assetDir, File targetDir) throws IOException {
        AssetManager assetManager = context.getAssets();
        String[] assets = assetManager.list(assetDir);
        if (assets == null || assets.length == 0) {
            // 是文件，直接复制
            copyAssetFile(context, assetDir, targetDir);
        } else {
            // 是目录，创建目标文件夹
            if (!targetDir.exists()) targetDir.mkdirs();
            for (String asset : assets) {
                String subAssetPath = assetDir + "/" + asset;
                File subTarget = new File(targetDir, asset);
                copyAssetsFolder(context, subAssetPath, subTarget);
            }
        }
    }

    /**
     * 复制单个 assets 文件
     */
    private static void copyAssetFile(Context context, String assetPath, File outFile) throws IOException {
        AssetManager assetManager = context.getAssets();
        InputStream is = assetManager.open(assetPath);
        File parent = outFile.getParentFile();
        if (!parent.exists()) {
            parent.mkdirs();
        }

        FileOutputStream fos = new FileOutputStream(outFile);
        byte[] buffer = new byte[4096];
        int read;
        while ((read = is.read(buffer)) != -1) {
            fos.write(buffer, 0, read);
        }
        fos.flush();
        fos.close();
        is.close();

        // 设置可执行权限（如果是二进制）
        outFile.setExecutable(true);
    }
}

package com.orbit;

import android.content.Context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import java.io.File;
import java.io.InputStream;

import aar.FileTransferProvider;

public class AndroidFileTransferProvider implements FileTransferProvider {

    private final Context context;

    public AndroidFileTransferProvider(Context context) {
        this.context = context;
    }

    @Override
    public byte[] getConfigData() throws Exception {
        InputStream is = context.getAssets().open("filetransfer.yml");
        YAMLMapper yamlMapper = new YAMLMapper();
        JsonNode root = yamlMapper.readTree(is);
        is.close();

        String nativeLibDir = context.getApplicationInfo().nativeLibraryDir;
        String newRclonePath = nativeLibDir + "/librclone.so"; // 你根据实际文件名改
        ObjectNode rootObj = (ObjectNode) root;
        ObjectNode rcloneNode;
        if (rootObj.has("rclone") && rootObj.get("rclone").isObject()) {
            rcloneNode = (ObjectNode) rootObj.get("rclone");
        } else {
            rcloneNode = yamlMapper.createObjectNode();
            rootObj.set("rclone", rcloneNode);
        }
        rcloneNode.put("path", newRclonePath);

        File pinyinTargetFile = new File(context.getFilesDir(), "pinyin.txt");
        if(!pinyinTargetFile.exists()) {
            StreamerService.copyAssetsFolder(context, "pinyin.txt", pinyinTargetFile);
        }
        rootObj.put("pinyin-path", pinyinTargetFile.getAbsolutePath());

        byte[] data = yamlMapper.writeValueAsBytes(root);
        return data;
    }
}

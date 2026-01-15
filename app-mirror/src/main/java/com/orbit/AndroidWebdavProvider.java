package com.orbit;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;

import androidx.documentfile.provider.DocumentFile;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import aar.FSProvider;
import aar.File;
import aar.FileInfo;
import aar.WebdavProvider;

// version is set by maven via filtering
public class AndroidWebdavProvider implements FSProvider, WebdavProvider {

    private final Context context;

    public AndroidWebdavProvider(Context context) {
        this.context = context;
    }

    @Override
    public byte[] listFiles(String s, long count) throws Exception {
        Uri uri = getRootUri();
        DocumentFile file = findDocumentFile(uri, s);
        Stream<DocumentFile> stream = Arrays.stream(file.listFiles());
        if(count > 0) {
            stream = stream.limit(count);
        }
        Collection<Map<String, Object>> children = stream
                .map(child -> {
                    Map<String, Object> properties = new HashMap<>();
                    properties.put("name", child.getName());
                    properties.put("size", child.length());
                    properties.put("mod-time", child.lastModified());
                    properties.put("dir", child.isDirectory());
                    return properties;
                })
                .collect(Collectors.toCollection(LinkedList::new));
        return new ObjectMapper().writeValueAsBytes(children);
    }

    @Override
    public void mkdir(String s) throws Exception {
        Uri uri = getRootUri();
        DocumentFile file = findDocumentFile(uri, new java.io.File(s).getParent());
        file.createDirectory(s);
    }

    @Override
    public File openFile(String s, long l) throws Exception {
        Uri uri = getRootUri();
        DocumentFile docFile = findDocumentFile(uri, s);
        FileInfo info = new FileInfo();
        info.setName(docFile.getName());
        info.setSize(docFile.length());
        info.setModTime(docFile.lastModified());
        info.setIsDir(docFile.isDirectory());
        File file = new File();
        file.setFileInfo(info);
        if(!docFile.isDirectory()) {
            ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(docFile.getUri(), convertGoFlagsToMode((int) l));
            // 获取原始 FD (int)
            int fd = pfd.detachFd();
            file.setFileDescriptor(fd);
        } else {
            file.setFileDescriptor(-1);
        }
        return file;
    }

    @Override
    public void removeAll(String s) throws Exception {
        Uri uri = getRootUri();
        DocumentFile file = findDocumentFile(uri, s);
        file.delete();
    }

    @Override
    public void rename(String oldPath, String newPath) throws Exception {
        Uri rootUri = getRootUri();
        String oldParentPath = new java.io.File(oldPath).getParent();
        String newParentPath = new java.io.File(newPath).getParent();
        String oldName = new java.io.File(oldPath).getName();
        if(!newParentPath.equals(oldParentPath)) {
            DocumentFile oldParentFile = findDocumentFile(rootUri, oldParentPath);
            DocumentFile oldFile = oldParentFile.findFile(oldName);
            DocumentFile newParentFile = findDocumentFile(rootUri, newParentPath);
            ContentResolver resolver = context.getContentResolver();
            DocumentsContract.moveDocument(resolver, oldFile.getUri(), oldParentFile.getUri(), newParentFile.getUri());
        }
        if(!new java.io.File(newPath).getName().equals(oldName)) {
            DocumentFile oldFile = findDocumentFile(rootUri, oldName);
            String newName = new java.io.File(newPath).getName();
            oldFile.renameTo(newName);
        }
    }

    @Override
    public FileInfo stat(String s) throws Exception {
        Uri uri = getRootUri();
        DocumentFile file = findDocumentFile(uri, s);
        FileInfo info = new FileInfo();
        info.setModTime(file.lastModified());
        info.setSize(file.length());
        info.setIsDir(file.isDirectory());
        info.setName(file.getName());
        return info;
    }

    private Uri getRootUri() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String rootUriStr = prefs.getString("dir_uri", null);
        Uri rootUri = Uri.parse(rootUriStr);
        return rootUri;
    }

    private DocumentFile findDocumentFile(Uri rootUri, String path) {
        if (rootUri == null || path == null || path.isBlank()) {
            throw new RuntimeException();
        }

        DocumentFile currentFile = DocumentFile.fromTreeUri(context, rootUri);
        String pathStr = path.replaceFirst("^\\s*/+", "");
        //java的split有点扯，所以先特殊处理
        if(pathStr.isEmpty()) {
            return currentFile;
        }

        String[] parts = pathStr.split("/+"); // 按 / 分割路径

        for (String part : parts) {
            currentFile = currentFile.findFile(part);
            if (currentFile == null) {
                throw new RuntimeException();
            }
        }
        return currentFile;
    }

    /**
     * 专门将 Go 的 os Flags 转换为 Android openFileDescriptor 需要的 Mode 字符串
     */
    private static String convertGoFlagsToMode(int goFlags) {
        // Go 常量硬编码 (POSIX 标准):
        // O_RDONLY: 0, O_WRONLY: 1, O_RDWR: 2, O_CREATE: 64, O_TRUNC: 512, O_APPEND: 1024

        int accessMode = goFlags & 3; // 掩码获取读写位 (最后两位)

        if (accessMode == 2) {        // os.O_RDWR
            return "rw";
        } else if (accessMode == 1) { // os.O_WRONLY
            if ((goFlags & 512) != 0) {      // os.O_TRUNC
                return "wt";
            } else if ((goFlags & 1024) != 0) { // os.O_APPEND
                return "wa";
            }
            return "w";
        }

        // 默认 O_RDONLY (0)
        return "r";
    }
}
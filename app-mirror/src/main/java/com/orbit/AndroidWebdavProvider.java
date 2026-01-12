package com.orbit;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.util.Log;

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

// version is set by maven via filtering
public class AndroidWebdavProvider implements FSProvider {

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
                    properties.put("name", new java.io.File(s, child.getName()).getPath());
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
        DocumentFile file = findDocumentFile(uri, s);
        File result = new File();
        result.setFileInfo(s, file.length(), file.lastModified(), file.isDirectory());
        if(!file.isDirectory()) {
            ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(file.getUri(), convertGoFlagsToMode((int) l));
            // 获取原始 FD (int)
            int fd = pfd.detachFd();
            result.setFileDescriptor(fd);
        } else {
            result.setFileDescriptor(-1);
        }
        return result;
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
        Uri newParentUri = findDocumentUri(rootUri, newParentPath);
        ContentResolver resolver = context.getContentResolver();
        if(!newParentPath.equals(oldParentPath)) {
            Uri oldUri = findDocumentUri(rootUri, oldPath);
            Uri oldParentUri = findDocumentUri(rootUri, oldParentPath);
            DocumentsContract.moveDocument(resolver, oldUri, oldParentUri, newParentUri);
        }
        String newName = new java.io.File(newPath).getName();
        Uri newUri = findDocumentUri(rootUri, newPath);
        DocumentsContract.renameDocument(resolver, newUri, newName);
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

    private Uri findDocumentUri(Uri rootUri, String relativePath) {
        if (rootUri == null || relativePath == null || relativePath.isEmpty()) {
            throw new RuntimeException();
        }

        String[] parts = relativePath.split("/+"); // 按 / 分割路径
        Uri currentUri = rootUri;

        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }

            currentUri = findChildByName(currentUri, part);
            if (currentUri == null) {
                throw new RuntimeException();
            }
        }

        return currentUri;
    }

    /**
     * 根据 rootUri（用户授权的 SAF 根目录）和相对路径找到对应的 DocumentFile
     *
     * @param rootUri    SAF 授权的根目录 Uri
     * @param relativePath 相对路径，例如 "a/b/c.txt"
     * @return DocumentFile 对象，找不到返回 null
     */
    private DocumentFile findDocumentFile(Uri rootUri, String relativePath) {
        Uri pathUri = findDocumentUri(rootUri, relativePath);
        return DocumentFile.fromTreeUri(context, pathUri);
    }

    /**
     * 在指定父目录 Uri 下查找名字为 name 的子文件或子文件夹
     *
     * @param parentUri 父目录 Uri
     * @param name      子文件/文件夹名
     * @return 子项 Uri，找不到返回 null
     */
    private Uri findChildByName(Uri parentUri, String name) {
        Cursor cursor = null;
        try {
            // 构建当前目录的子文件 Uri
            Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                    parentUri,
                    DocumentsContract.getTreeDocumentId(parentUri)
            );

            // 查询名字匹配的文件/文件夹
            cursor = context.getContentResolver().query(
                    childrenUri,
                    new String[]{
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            DocumentsContract.Document.COLUMN_MIME_TYPE
                    },
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME + "=?",
                    new String[]{name},
                    null
            );

            if (cursor != null) {
                Log.d("WebDAV", "查询目标名字: " + name + " | 结果总数: " + cursor.getCount());
                while (cursor.moveToNext()) {
                    Log.d("WebDAV", "Cursor当前指向的名字: " + cursor.getString(1) + " | ID: " + cursor.getString(0));
                    if (name.equals(cursor.getString(1))) {
                        // 只有名字完全匹配才返回
                        String docId = cursor.getString(0);
                        return DocumentsContract.buildDocumentUriUsingTree(parentUri, docId);
                    }
                }
                cursor.close();
            }
            throw new RuntimeException();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
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
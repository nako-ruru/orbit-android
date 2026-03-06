package com.orbit;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.util.Pair;
import android.webkit.MimeTypeMap;

import androidx.documentfile.provider.DocumentFile;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import aar.FSProvider;
import aar.FileInfo;
import aar.WebdavProvider;

// version is set by maven via filtering
public class AndroidWebdavProvider implements FSProvider, WebdavProvider {

    private final Context context;

    public AndroidWebdavProvider(Context context) {
        this.context = context;
    }

    @Override
    public byte[] readDir(String path, boolean recursive, long count) throws Exception {
        List<FileInfo> children;
        if(path.matches("^\\s*/+\\s*$")) {
            children = getMountDocumentFiles()
                    .map(p -> SimpleFileInfo.create(p.second.isDirectory(), p.first, p.second.length(), p.second.lastModified(), p.second.getType()))
                    .collect(Collectors.toCollection(ArrayList::new));
        } else {
            Uri uri = getRootUri(path);
            DocumentFile file = findDocumentFile(uri, path);
            children = listChildrenMetadata(context, file, count);
        }
        return new ObjectMapper().writeValueAsBytes(children);
    }

    @Override
    public void copy(String src, String target) throws Exception {
        throw new RuntimeException();
    }

    @Override
    public void mkdir(String s) throws Exception {
        Uri uri = getRootUri(s);
        java.io.File parentFile = new java.io.File(s);
        DocumentFile parentDocFile = findDocumentFile(uri, parentFile.getParent());
        if(parentDocFile.findFile(parentFile.getName()) == null) {
            parentDocFile.createDirectory(parentFile.getName());
        }
    }

    @Override
    public long open(String s, long goFlags) throws Exception {
        Uri uri = getRootUri(s);
        DocumentFile docFile;
        if ((goFlags & 64) != 0) {
            DocumentFile parentDocFile = findDocumentFile(uri, new java.io.File(s).getParent());
            String fileName = new java.io.File(s).getName();
            docFile = parentDocFile.findFile(fileName);
            if (docFile == null || !docFile.exists()) {
                docFile = parentDocFile.createFile(getMimeType(fileName), fileName); // 真正的创建动作
            }
        } else {
            docFile = findDocumentFile(uri, s);
        }
        ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(docFile.getUri(), convertGoFlagsToMode((int) goFlags));
        // 获取原始 FD (int)
        return pfd.detachFd();
    }

    @Override
    public void removeAll(String s) throws Exception {
        Uri uri = getRootUri(s);
        DocumentFile file = findDocumentFile(uri, s);
        file.delete();
    }

    @Override
    public void rename(String oldPath, String newPath) throws Exception {
        Uri newRootUri = getRootUri(newPath);
        String oldParentPath = new java.io.File(oldPath).getParent();
        String newParentPath = new java.io.File(newPath).getParent();
        String oldName = new java.io.File(oldPath).getName();
        if(!newParentPath.equals(oldParentPath)) {
            DocumentFile oldParentFile = findDocumentFile(getRootUri(oldPath), oldParentPath);
            DocumentFile oldFile = oldParentFile.findFile(oldName);
            DocumentFile newParentFile = findDocumentFile(newRootUri, newParentPath);
            ContentResolver resolver = context.getContentResolver();
            DocumentsContract.moveDocument(resolver, oldFile.getUri(), oldParentFile.getUri(), newParentFile.getUri());
        }
        if(!new java.io.File(newPath).getName().equals(oldName)) {
            DocumentFile oldFile = findDocumentFile(newRootUri, oldName);
            String newName = new java.io.File(newPath).getName();
            oldFile.renameTo(newName);
        }
    }

    @Override
    public FileInfo stat(String s) throws Exception {
        FileInfo info;
        if(s.matches("^\\s*/+\\s*$")) {
            info = SimpleFileInfo.createDir();
        } else {
            Uri uri = getRootUri(s);
            DocumentFile file = findDocumentFile(uri, s);
            info = SimpleFileInfo.create(file.isDirectory(), file.getName(), file.length(), file.lastModified(), file.getType());
        }
        return info;
    }

    private Stream<Pair<String, DocumentFile>> getMountDocumentFiles() throws JsonProcessingException {
        Set<MountPoint> mountPoints = getMountPoints();
        return mountPoints.stream()
                .map(m -> {
                    DocumentFile doc = DocumentFile.fromTreeUri(context, Uri.parse(m.getUri()));
                    return Pair.create(String.format("%s@%s", doc.getName(), m.getRootId()), doc);
                });
    }

    private Uri getRootUri(String path) throws JsonProcessingException {
        Matcher matcher = Pattern.compile("^\\s*/([^/]*)").matcher(path);
        if(!matcher.find()) {
            throw new RuntimeException();
        }
        String mountName = matcher.group(1);
        return getRootUriByMountName(mountName);
    }

    private Uri getRootUriByMountName(String mountName) throws JsonProcessingException {
        Set<MountPoint> mountPoints = getMountPoints();
        String[] parts = mountName.split("@(?=[^@]*$)");
        MountPoint mp = mountPoints.stream()
                .filter(m -> m.getRootId().equals(parts[1]))
                .findAny()
                .orElseThrow();
        return Uri.parse(mp.getUri());
    }

    private Set<MountPoint> getMountPoints() throws JsonProcessingException {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String rootUriStr = prefs.getString("dir_uris", null);
        if(rootUriStr == null || rootUriStr.isBlank()) {
            throw new RuntimeException();
        }
        Set<MountPoint> mountPoints = new ObjectMapper().readValue(rootUriStr,   new TypeReference<LinkedHashSet<MountPoint>>() {});
        return mountPoints;
    }

    private DocumentFile findDocumentFile(Uri rootUri, String path) {
        if (rootUri == null || path == null || path.isBlank()) {
            throw new RuntimeException("Invalid path or rootUri");
        }
        // 1. 预处理路径（保留你原来的逻辑）
        String pathStr = path.replaceFirst("^\\s*/[^/]*(/|$)", "");
        pathStr = pathStr.replaceFirst("/+$", "");
        if (pathStr.isEmpty()) {
            return DocumentFile.fromTreeUri(context, rootUri);
        }

        // 获取根目录 ID 准备迭代
        String currentDocId = DocumentsContract.getTreeDocumentId(rootUri);
        String[] parts = pathStr.split("/+"); // 按 / 分割路径
        // 2. 使用高效的 Cursor 迭代寻找最终的 Document ID
        for (String part : parts) {
            Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(rootUri, currentDocId);
            String[] projection = { DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME };

            boolean found = false;
            try (Cursor cursor = context.getContentResolver().query(childrenUri, projection, null, null, null)) {
                if (cursor != null) {
                    int idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
                    int nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
                    while (cursor.moveToNext()) {
                        if (part.equals(cursor.getString(nameIdx))) {
                            currentDocId = cursor.getString(idIdx);
                            found = true;
                            break;
                        }
                    }
                }
            }

            if (!found) {
                throw new RuntimeException("Path not found: " + part);
            }
        }

        // 3. 核心：根据最终 DocId 构建一个具有“树权限”的 SingleUri
        // 只有这样构建的 Uri 才能让 DocumentFile 对象继续拥有读写权限
        Uri finalUri = DocumentsContract.buildDocumentUriUsingTree(rootUri, currentDocId);

        // 返回 DocumentFile 接口，完美兼容原有代码
        return DocumentFile.fromSingleUri(context, finalUri);
    }

    public static List<FileInfo> listChildrenMetadata(Context context, DocumentFile parentDocFile, long count) {

        List<FileInfo> metadataList = new ArrayList<>();

        Uri parentDocUri = parentDocFile.getUri();
        // 1. 从 Uri 中提取 Document ID
        String parentDocId = DocumentsContract.getDocumentId(parentDocUri);

        // 2. 构建指向子文件的查询 Uri
        // 注意：如果是从 TreeUri 获得的，建议使用 buildChildDocumentsUriUsingTree
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parentDocUri, parentDocId);

        // 3. 定义我们一次性要取回的列 (Projection)
        String[] projection = new String[]{
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };

        // 4. 只进行一次 query，获取整个结果集
        try (Cursor cursor = context.getContentResolver().query(childrenUri, projection, null, null, null)) {
            if (cursor != null) {
                while (cursor.moveToNext() && (count <= 0 || metadataList.size() < count)) {
                    // 判断是否为目录
                    String mimeType = cursor.getString(3);
                    FileInfo data = SimpleFileInfo.create(
                            DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType),
                            cursor.getString(0),
                            cursor.getLong(1), cursor.getLong(2), mimeType
                    );
                    metadataList.add(data);
                }
            }
        }

        return metadataList;
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

    private static String getMimeType(String fileName) {
        String extension = MimeTypeMap.getFileExtensionFromUrl(fileName);
        if (extension != null) {
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
            if (mime != null) return mime;
        }
        return "application/octet-stream"; // 兜底方案
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    private static class SimpleFileInfo implements FileInfo {

        private static SimpleFileInfo createDir() {
            SimpleFileInfo info = new SimpleFileInfo();
            info.dir = true;
            return info;
        }

        private static SimpleFileInfo create(boolean dir, String name, long size, long modTime, String mimeType) {
            SimpleFileInfo info = new SimpleFileInfo();
            info.dir = dir;
            info.name = name;
            info.modTime = modTime;
            info.size = size;
            return info;
        }

        private boolean dir;
        @JsonProperty("mod-time")
        private long modTime;
        private long size;
        private String name;
        @JsonProperty("mime-type")
        private String mimeType;

        private SimpleFileInfo() {
        }
        @Override
        public boolean isDir() {
            return dir;
        }
        @Override
        public long modTime() {
            return modTime;
        }
        @Override
        public String name() {
            return name;
        }
        @Override
        public long size() {
            return size;
        }
        @Override
        public String mimeType() {
            return mimeType;
        }
    }
}
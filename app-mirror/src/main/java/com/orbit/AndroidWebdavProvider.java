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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
        Stream<Map<String, Object>> childStream;
        if(s.matches("^\\s*/+\\s*$")) {
            childStream = getMountDocumentFiles()
                    .map(p -> {
                        Map<String, Object> properties = new HashMap<>();
                        properties.put("name", p.first);
                        properties.put("size", p.second.length());
                        properties.put("mod-time", p.second.lastModified());
                        properties.put("dir", p.second.isDirectory());
                        return properties;
                    });
        } else {
            Uri uri = getRootUri(s);
            DocumentFile file = findDocumentFile(uri, s);
            Stream<DocumentFile> stream = Arrays.stream(file.listFiles());
            if(count > 0) {
                stream = stream.limit(count);
            }
            childStream = stream
                    .map(child -> {
                        Map<String, Object> properties = new HashMap<>();
                        properties.put("name", child.getName());
                        properties.put("size", child.length());
                        properties.put("mod-time", child.lastModified());
                        properties.put("dir", child.isDirectory());
                        return properties;
                    });
        }
        Collection<Map<String, Object>> children = childStream.collect(Collectors.toCollection(LinkedList::new));
        return new ObjectMapper().writeValueAsBytes(children);
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
    public File openFile(String s, long goFlags) throws Exception {
        File file = new File();
        FileInfo info = new FileInfo();
        file.setFileInfo(info);
        if(s.matches("^\\s*/+\\s*$")) {
            info.setIsDir(true);
        } else {
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
            info.setName(docFile.getName());
            info.setSize(docFile.length());
            info.setModTime(docFile.lastModified());
            info.setIsDir(docFile.isDirectory());
            if(!docFile.isDirectory()) {
                ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(docFile.getUri(), convertGoFlagsToMode((int) goFlags));
                // 获取原始 FD (int)
                int fd = pfd.detachFd();
                file.setFileDescriptor(fd);
            } else {
                file.setFileDescriptor(-1);
            }
        }
        return file;
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
        FileInfo info = new FileInfo();
        if(s.matches("^\\s*/+\\s*$")) {
            info.setIsDir(true);
        } else {
            Uri uri = getRootUri(s);
            DocumentFile file = findDocumentFile(uri, s);
            info.setModTime(file.lastModified());
            info.setSize(file.length());
            info.setIsDir(file.isDirectory());
            info.setName(file.getName());
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

    private Map<String, String> documentIdCache = new TreeMap<>();

    private DocumentFile findDocumentFile(Uri rootUri, String path) {
        // 1. 标准化路径格式
        String normalizedPath = "/" + path.replaceAll("^/+|/+$", "");
        String[] parts = normalizedPath.split("/");

        // 如果只有挂载点（如 "/InternalStorage"），直接返回根
        if (parts.length <= 2 && normalizedPath.split("/").length <= 2) {
            return DocumentFile.fromTreeUri(context, rootUri);
        }

        // 2. 逆序回溯查找缓存中的最深祖先
        String foundDocId = null;
        int firstMissingIndex = parts.length;

        for (int i = parts.length - 1; i >= 1; i--) {
            String subPath = buildFullPath(parts, i);
            foundDocId = documentIdCache.get(subPath);
            if (foundDocId != null) {
                firstMissingIndex = i + 1;
                break;
            }
        }

        // 3. 如果缓存完全没中，从挂载点根目录开始
        if (foundDocId == null) {
            foundDocId = DocumentsContract.getTreeDocumentId(rootUri);
            firstMissingIndex = 2; // parts[1]是挂载点，从parts[2]开始找子节点
        }

        // 4. 正向补全缺失路径：逐级通过父 ID 查找子 ID
        String currentParentId = foundDocId;
        for (int i = firstMissingIndex; i < parts.length; i++) {
            String targetName = parts[i];
            // 纯粹的查找：只找目标，不干多余的事
            String childId = findSingleChildId(rootUri, currentParentId, targetName);

            if (childId == null) return null; // 物理路径不存在

            // 缓存当前层级 ID
            String currentPath = buildFullPath(parts, i);
            documentIdCache.put(currentPath, childId);

            currentParentId = childId;
        }

        // 5. 合成并返回轻量级 DocumentFile
        Uri targetUri = DocumentsContract.buildDocumentUriUsingTree(rootUri, currentParentId);
        return DocumentFile.fromSingleUri(context, targetUri);
    }

    private String buildFullPath(String[] parts, int index) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= index; i++) {
            sb.append("/").append(parts[i]);
        }
        return sb.toString();
    }

    private String findSingleChildId(Uri rootUri, String parentId, String targetName) {
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(rootUri, parentId);
        // 这里只查询 ID 和 名字，性能最优
        try (Cursor cursor = context.getContentResolver().query(childrenUri,
                new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                null, null, null)) {

            while (cursor != null && cursor.moveToNext()) {
                if (targetName.equals(cursor.getString(1))) {
                    return cursor.getString(0);
                }
            }
        }
        return null;
    }

    private DocumentFile slowFindDocumentFile(Uri rootUri, String path) {
        if (rootUri == null || path == null || path.isBlank()) {
            throw new RuntimeException();
        }

        DocumentFile currentFile = DocumentFile.fromTreeUri(context, rootUri);
        String pathStr = path.replaceFirst("^\\s*/[^/]*(/|$)", "");
        pathStr = pathStr.replaceFirst("/+$", "");
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

    private static String getMimeType(String fileName) {
        String extension = MimeTypeMap.getFileExtensionFromUrl(fileName);
        if (extension != null) {
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
            if (mime != null) return mime;
        }
        return "application/octet-stream"; // 兜底方案
    }
}
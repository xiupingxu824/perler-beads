package com.perlerbeads.file;

import com.perlerbeads.common.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
@CrossOrigin(originPatterns = "*")
public class FileController {
    private final FileMapper fileMapper;
    @Value("${perler.upload-dir:./uploads}") private String uploadDir;
    public FileController(FileMapper fileMapper) { this.fileMapper = fileMapper; }
    @PostMapping("/upload")
    public ApiResponse<FileView> upload(@RequestParam MultipartFile file) throws IOException {
        if (file.isEmpty()) return ApiResponse.fail("请选择图片文件");
        String suffix = OptionalSuffix.of(file.getOriginalFilename());
        Path dir = Paths.get(uploadDir).toAbsolutePath().normalize(); Files.createDirectories(dir);
        String name = UUID.randomUUID() + suffix;
        Path target = dir.resolve(name).normalize();
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        String fileId = UUID.randomUUID().toString();
        long size = file.getSize();

        FileEntity entity = new FileEntity(); entity.setId(fileId);entity.setOriginalName(file.getOriginalFilename()); entity.setFileName(name); entity.setFilePath(target.toString()); entity.setContentType(file.getContentType()); entity.setFileSize(size); fileMapper.insert(entity);
        FileView fileView = new FileView(fileId, name, "/uploads/" + name, file.getContentType(), file.getSize());

        return ApiResponse.ok(fileView);
    }
    public static class FileView {
        private String fileId;
        private String storageName;
        private String url;
        private String contentType;
        private long size;

        public FileView(String fileId, String storageName, String url, String contentType, long size) {
            this.fileId = fileId;
            this.storageName = storageName;
            this.url = url;
            this.contentType = contentType;
            this.size = size;
        }

        public String getFileId() {
            return fileId;
        }

        public void setFileId(String fileId) {
            this.fileId = fileId;
        }

        public String getStorageName() {
            return storageName;
        }

        public void setStorageName(String storageName) {
            this.storageName = storageName;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public long getSize() {
            return size;
        }

        public void setSize(long size) {
            this.size = size;
        }
    }
    static final class OptionalSuffix { static String of(String name) { if (name == null || !name.contains(".")) return ".png"; return name.substring(name.lastIndexOf('.')).toLowerCase(); } }
}

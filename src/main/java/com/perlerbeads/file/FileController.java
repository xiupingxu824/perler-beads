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
        Path dir = Paths.get(uploadDir); Files.createDirectories(dir);
        String name = UUID.randomUUID() + suffix;
        Files.copy(file.getInputStream(), dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        FileEntity entity = new FileEntity(); entity.setOriginalName(file.getOriginalFilename()); entity.setFileName(name); entity.setFilePath(dir.resolve(name).toAbsolutePath().toString()); entity.setContentType(file.getContentType()); entity.setFileSize(file.getSize()); fileMapper.insert(entity);
        return ApiResponse.ok(new FileView(entity.getId(), name, "/uploads/" + name, file.getContentType(), file.getSize()));
    }
    public record FileView(Long id, String fileName, String url, String contentType, long size) {}
    static final class OptionalSuffix { static String of(String name) { if (name == null || !name.contains(".")) return ".png"; return name.substring(name.lastIndexOf('.')).toLowerCase(); } }
}

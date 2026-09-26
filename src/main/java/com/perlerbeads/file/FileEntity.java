package com.perlerbeads.file;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_file")
public class FileEntity {
    @TableId
    private String id;
    private String originalName;
    private String fileName;
    private String filePath;
    private String contentType;
    private Long fileSize;
    private LocalDateTime createTime;
}

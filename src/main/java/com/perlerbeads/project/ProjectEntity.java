package com.perlerbeads.project;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("pattern_project")
public class ProjectEntity {
    @TableId private Long id; private Long userId; private String name; private String description;
    private Long sourceImageId; private Long previewImageId; private Integer width; private Integer height;
    private Long brandId; private Integer maxColors; private Integer actualColorCount; private Integer totalBeads;
    private String patternData; private String status; private Integer isPublic; private LocalDateTime createTime; private LocalDateTime updateTime; private Integer deleted;
}

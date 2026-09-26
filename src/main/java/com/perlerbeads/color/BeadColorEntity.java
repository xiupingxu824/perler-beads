package com.perlerbeads.color;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("bead_color")
public class BeadColorEntity {
    private Long id; private Long brandId; private String colorCode; private String colorName; private String hex;
    private Integer rgbR; private Integer rgbG; private Integer rgbB; private Integer status; private Integer sort;
}

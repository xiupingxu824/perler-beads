package com.perlerbeads.color;

import com.perlerbeads.common.ApiResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/colors")
@CrossOrigin(origins = "http://localhost:5173")
public class ColorController {
    private final BeadColorMapper mapper;
    public ColorController(BeadColorMapper mapper) { this.mapper = mapper; }
    @GetMapping
    public ApiResponse<List<ColorView>> list(@RequestParam(defaultValue = "1") Long brandId) {
        return ApiResponse.ok(mapper.selectList(new LambdaQueryWrapper<BeadColorEntity>().eq(BeadColorEntity::getBrandId, brandId).eq(BeadColorEntity::getStatus, 1).orderByAsc(BeadColorEntity::getSort)).stream().map(c -> new ColorView(c.getColorCode(), c.getColorName(), c.getHex())).toList());
    }
    public record ColorView(String code, String name, String hex) {}
}

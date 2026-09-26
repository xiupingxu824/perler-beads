package com.perlerbeads.color;

import com.perlerbeads.common.ApiResponse;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/colors")
@CrossOrigin(origins = "http://localhost:5173")
public class ColorController {
    @GetMapping
    public ApiResponse<List<ColorView>> list() {
        return ApiResponse.ok(List.of(
            new ColorView("A01", "珊瑚红", "#FF6B6B"), new ColorView("A02", "奶油黄", "#FFD166"),
            new ColorView("A03", "晴空蓝", "#70D6FF"), new ColorView("A04", "薄荷绿", "#8CE99A"),
            new ColorView("A05", "薰衣草", "#A78BFA"), new ColorView("A06", "蜜桃橙", "#FF9F68"),
            new ColorView("A07", "深灰", "#34313F"), new ColorView("A08", "象牙白", "#FFFDF8")
        ));
    }
    public record ColorView(String code, String name, String hex) {}
}

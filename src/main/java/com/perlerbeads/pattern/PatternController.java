package com.perlerbeads.pattern;

import com.perlerbeads.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pattern")
@CrossOrigin(originPatterns = "*")
public class PatternController {
    private final PatternService patternService;
    public PatternController(PatternService patternService) { this.patternService = patternService; }

    @PostMapping("/generate")
    public ApiResponse<PatternDtos.GenerateResponse> generate(@Valid @RequestBody PatternDtos.GenerateRequest request) {
        try {
            return ApiResponse.ok(patternService.generate(request));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.fail(ex.getMessage());
        }
    }
}

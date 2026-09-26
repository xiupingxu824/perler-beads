package com.perlerbeads.pattern;

import com.perlerbeads.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pattern")
@CrossOrigin(origins = "http://localhost:5173")
public class PatternController {
    private final PatternService patternService;
    public PatternController(PatternService patternService) { this.patternService = patternService; }

    @PostMapping("/generate")
    public ApiResponse<PatternDtos.GenerateResponse> generate(@Valid @RequestBody PatternDtos.GenerateRequest request) {
        return ApiResponse.ok(patternService.generate(request));
    }
}

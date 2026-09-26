package com.perlerbeads.pattern;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public final class PatternDtos {
    private PatternDtos() {}
    public record GenerateRequest(@NotNull @Min(4) @Max(200) Integer width,
                                  @NotNull @Min(4) @Max(200) Integer height,
                                  @Min(2) @Max(64) Integer maxColors,
                                  Long brandId,
                                  Boolean keepRatio,
                                  Boolean dithering) {}
    public record ColorItem(String code, String name, String hex, int quantity) {}
    public record GenerateResponse(int width, int height, int totalBeads, List<ColorItem> colors, List<List<String>> matrix) {}
}

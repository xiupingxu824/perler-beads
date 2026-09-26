package com.perlerbeads.pattern;

import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class PatternService {
    private static final List<String[]> PALETTE = List.of(
        new String[]{"A01", "珊瑚红", "#FF6B6B"}, new String[]{"A02", "奶油黄", "#FFD166"},
        new String[]{"A03", "晴空蓝", "#70D6FF"}, new String[]{"A04", "薄荷绿", "#8CE99A"},
        new String[]{"A05", "薰衣草", "#A78BFA"}, new String[]{"A06", "蜜桃橙", "#FF9F68"},
        new String[]{"A07", "深灰", "#34313F"}, new String[]{"A08", "象牙白", "#FFFDF8"}
    );

    public PatternDtos.GenerateResponse generate(PatternDtos.GenerateRequest request) {
        int width = request.width(), height = request.height();
        List<List<String>> matrix = new ArrayList<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int row = 0; row < height; row++) {
            List<String> line = new ArrayList<>();
            for (int col = 0; col < width; col++) {
                int distance = Math.abs(row - height / 2) + Math.abs(col - width / 2);
                String[] color = distance < Math.min(width, height) / 5 ? PALETTE.get((row + col) % 6) : PALETTE.get(7);
                line.add(color[0]); counts.merge(color[0], 1, Integer::sum);
            }
            matrix.add(line);
        }
        List<PatternDtos.ColorItem> colors = PALETTE.stream().filter(p -> counts.containsKey(p[0]))
            .map(p -> new PatternDtos.ColorItem(p[0], p[1], p[2], counts.get(p[0]))).toList();
        return new PatternDtos.GenerateResponse(width, height, width * height, colors, matrix);
    }
}

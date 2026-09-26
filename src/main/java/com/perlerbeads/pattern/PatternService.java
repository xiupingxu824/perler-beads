package com.perlerbeads.pattern;

import org.springframework.stereotype.Service;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;
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
        BufferedImage image = decodeImage(request.imageBase64());
        for (int row = 0; row < height; row++) {
            List<String> line = new ArrayList<>();
            for (int col = 0; col < width; col++) {
                String[] color = image == null ? demoColor(row, col, width, height) : nearestColor(image.getRGB(Math.min(image.getWidth()-1, col * image.getWidth() / width), Math.min(image.getHeight()-1, row * image.getHeight() / height)));
                line.add(color[0]); counts.merge(color[0], 1, Integer::sum);
            }
            matrix.add(line);
        }
        List<PatternDtos.ColorItem> colors = PALETTE.stream().filter(p -> counts.containsKey(p[0]))
            .map(p -> new PatternDtos.ColorItem(p[0], p[1], p[2], counts.get(p[0]))).toList();
        return new PatternDtos.GenerateResponse(width, height, width * height, colors, matrix);
    }

    private String[] demoColor(int row, int col, int width, int height) {
        int distance = Math.abs(row - height / 2) + Math.abs(col - width / 2);
        return distance < Math.min(width, height) / 5 ? PALETTE.get((row + col) % 6) : PALETTE.get(7);
    }

    private String[] nearestColor(int rgb) {
        int r=(rgb>>16)&255,g=(rgb>>8)&255,b=rgb&255; String[] best=PALETTE.get(0); double min=Double.MAX_VALUE;
        for (String[] color : PALETTE) { int n=Integer.parseInt(color[2].substring(1),16), cr=n>>16,cg=(n>>8)&255,cb=n&255; double d=Math.pow(r-cr,2)+Math.pow(g-cg,2)+Math.pow(b-cb,2); if(d<min){min=d;best=color;} }
        return best;
    }

    private BufferedImage decodeImage(String source) {
        try { if (source == null || source.isBlank()) return null; String raw=source.contains(",") ? source.substring(source.indexOf(',')+1) : source; return ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(raw))); }
        catch (Exception ignored) { return null; }
    }
}

package com.perlerbeads.pattern;

import org.springframework.stereotype.Service;
import com.perlerbeads.file.FileEntity;
import com.perlerbeads.file.FileMapper;
import com.perlerbeads.color.BeadColorEntity;
import com.perlerbeads.color.BeadColorMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Value;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.*;

@Service
public class PatternService {
    private final FileMapper fileMapper;
    private final BeadColorMapper colorMapper;
    @Value("${perler.upload-dir:./uploads}") private String uploadDir;
    public PatternService(FileMapper fileMapper, BeadColorMapper colorMapper) { this.fileMapper = fileMapper; this.colorMapper = colorMapper; }
    public PatternDtos.GenerateResponse generate(PatternDtos.GenerateRequest request) {
        int width = request.width(), height = request.height();
        List<PaletteColor> palette = loadPalette(request.brandId());
        if (palette.isEmpty()) throw new IllegalArgumentException("当前品牌没有可用颜色，请先维护 bead_color 颜色库");
        List<List<String>> matrix = new ArrayList<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        BufferedImage image = decodeImage(request);
        boolean keepRatio = !Boolean.FALSE.equals(request.keepRatio());
        for (int row = 0; row < height; row++) {
            List<String> line = new ArrayList<>();
            for (int col = 0; col < width; col++) {
                if (image == null) throw new IllegalArgumentException("无法读取上传图片，请重新上传图片");
                int[] sample = sampleRegion(image, col, row, width, height, keepRatio);
                PaletteColor color = nearestColor(palette, (sample[0] << 16) | (sample[1] << 8) | sample[2]);
                line.add(color.code()); counts.merge(color.code(), 1, Integer::sum);
            }
            matrix.add(line);
        }
        List<PatternDtos.ColorItem> colors = palette.stream()
            .map(p -> new PatternDtos.ColorItem(p.id(), p.code(), p.name(), p.hex(), counts.getOrDefault(p.code(), 0))).toList();
        return new PatternDtos.GenerateResponse(width, height, width * height, colors, matrix);
    }

    private PaletteColor nearestColor(List<PaletteColor> palette, int rgb) {
        int r=(rgb>>16)&255,g=(rgb>>8)&255,b=rgb&255; PaletteColor best=palette.get(0); double min=Double.MAX_VALUE;
        for (PaletteColor color : palette) { double d=Math.pow(r-color.r(),2)+Math.pow(g-color.g(),2)+Math.pow(b-color.b(),2); if(d<min){min=d;best=color;} }
        return best;
    }

    /** Samples the whole source-image area represented by one bead instead of taking one pixel. */
    private int[] sampleRegion(BufferedImage image, int col, int row, int targetWidth, int targetHeight, boolean keepRatio) {
        double sourceWidth = image.getWidth();
        double sourceHeight = image.getHeight();
        double offsetX = 0;
        double offsetY = 0;
        double visibleWidth = sourceWidth;
        double visibleHeight = sourceHeight;
        if (keepRatio) {
            double sourceRatio = sourceWidth / sourceHeight;
            double targetRatio = (double) targetWidth / targetHeight;
            if (sourceRatio > targetRatio) {
                visibleWidth = sourceHeight * targetRatio;
                offsetX = (sourceWidth - visibleWidth) / 2;
            } else if (sourceRatio < targetRatio) {
                visibleHeight = sourceWidth / targetRatio;
                offsetY = (sourceHeight - visibleHeight) / 2;
            }
        }
        int x0 = (int) Math.floor(offsetX + col * visibleWidth / targetWidth);
        int x1 = Math.max(x0 + 1, (int) Math.ceil(offsetX + (col + 1) * visibleWidth / targetWidth));
        int y0 = (int) Math.floor(offsetY + row * visibleHeight / targetHeight);
        int y1 = Math.max(y0 + 1, (int) Math.ceil(offsetY + (row + 1) * visibleHeight / targetHeight));
        x0 = Math.max(0, Math.min(image.getWidth() - 1, x0));
        y0 = Math.max(0, Math.min(image.getHeight() - 1, y0));
        x1 = Math.max(x0 + 1, Math.min(image.getWidth(), x1));
        y1 = Math.max(y0 + 1, Math.min(image.getHeight(), y1));
        long red = 0, green = 0, blue = 0, count = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int rgb = image.getRGB(x, y);
                red += (rgb >> 16) & 255;
                green += (rgb >> 8) & 255;
                blue += rgb & 255;
                count++;
            }
        }
        return new int[]{(int) (red / count), (int) (green / count), (int) (blue / count)};
    }

    private List<PaletteColor> loadPalette(Long brandId) {
        Long actualBrandId = brandId == null ? 1L : brandId;
        return colorMapper.selectList(new LambdaQueryWrapper<BeadColorEntity>().eq(BeadColorEntity::getBrandId, actualBrandId).eq(BeadColorEntity::getStatus, 1).orderByAsc(BeadColorEntity::getSort)).stream().map(c -> new PaletteColor(c.getId(), c.getColorCode(), c.getColorName(), c.getHex(), c.getRgbR(), c.getRgbG(), c.getRgbB())).toList();
    }

    private record PaletteColor(Long id, String code, String name, String hex, int r, int g, int b) {}

    private BufferedImage decodeImage(PatternDtos.GenerateRequest request) {
        try {
            if (request.fileId() != null) {
                FileEntity file=fileMapper.selectById(request.fileId()); if(file==null) return null;
                Path stored=Path.of(file.getFilePath()).normalize();
                if (!Files.exists(stored)) stored=Path.of(uploadDir).toAbsolutePath().normalize().resolve(file.getFileName()).normalize();
                if (!Files.exists(stored)) throw new IllegalArgumentException("上传文件不存在：" + stored);
                BufferedImage image=ImageIO.read(stored.toFile());
                if (image==null) throw new IllegalArgumentException("上传文件不是有效图片：" + stored);
                return image;
            }
            String source=request.imageBase64(); if (source == null || source.isBlank()) return null; String raw=source.contains(",") ? source.substring(source.indexOf(',')+1) : source; return ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(raw)));
        }
        catch (Exception ignored) { return null; }
    }
}

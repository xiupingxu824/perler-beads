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
                Sample sample = sampleRegion(image, col, row, width, height, keepRatio);
                PaletteColor color = nearestColor(palette, sample);
                line.add(color.code()); counts.merge(color.code(), 1, Integer::sum);
            }
            matrix.add(line);
        }
        List<PatternDtos.ColorItem> colors = palette.stream()
            .map(p -> new PatternDtos.ColorItem(p.id(), p.code(), p.name(), p.hex(), counts.getOrDefault(p.code(), 0))).toList();
        return new PatternDtos.GenerateResponse(width, height, width * height, colors, matrix);
    }

    /**
     * RGB distance tends to over-select grey and brown beads. LAB is closer to
     * how people perceive the difference between two colours, so palette
     * selection is performed in LAB space. The edge sample protects dark
     * outlines which would otherwise disappear into the average of a bead cell.
     */
    private PaletteColor nearestColor(List<PaletteColor> palette, Sample sample) {
        Lab averageLab = rgbToLab(sample.red(), sample.green(), sample.blue());
        Lab target = averageLab;
        Lab chromaticLab = rgbToLab(sample.colorRed(), sample.colorGreen(), sample.colorBlue());
        // A small but saturated region (for example the cyan iris inside a
        // black pupil) is visually more important than the arithmetic RGB
        // average. Prefer it when it occupies a meaningful part of the cell.
        if (sample.colorRatio() >= 0.08
            && chroma(chromaticLab) >= chroma(averageLab) + 5) {
            target = chromaticLab;
        }
        PaletteColor best = palette.get(0);
        double min = Double.MAX_VALUE;
        for (PaletteColor color : palette) {
            double d = labDistance(target, color.lab());
            if (d < min) { min = d; best = color; }
        }

        // Do not let a black pupil erase a saturated iris. Edge protection is
        // reserved for neutral/dark line art; coloured regions are decided by
        // their colour match above.
        if (sample.edgeContrast() >= 32 && sample.darkRatio() >= 0.14
            && chroma(target) < 34) {
            Lab edgeTarget = rgbToLab(sample.edgeRed(), sample.edgeGreen(), sample.edgeBlue());
            PaletteColor edgeBest = palette.get(0);
            double edgeMin = Double.MAX_VALUE;
            for (PaletteColor color : palette) {
                double d = labDistance(edgeTarget, color.lab());
                if (d < edgeMin) { edgeMin = d; edgeBest = color; }
            }
            // Only replace the average match when the dark edge is a
            // meaningful part of this cell. This keeps facial outlines and
            // line art while avoiding isolated dark pixels changing a cell.
            if (edgeBest.lab().l() + 8 < best.lab().l() && edgeMin <= min + 1500) {
                return edgeBest;
            }
        }
        return best;
    }

    /** Samples a cell, retaining both its average colour and high-contrast edge information. */
    private Sample sampleRegion(BufferedImage image, int col, int row, int targetWidth, int targetHeight, boolean keepRatio) {
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
        long darkRed = 0, darkGreen = 0, darkBlue = 0, darkCount = 0;
        long colorRed = 0, colorGreen = 0, colorBlue = 0, colorCount = 0;
        int minLuma = 255;
        int maxLuma = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 255;
                int g = (rgb >> 8) & 255;
                int b = rgb & 255;
                int luma = (299 * r + 587 * g + 114 * b) / 1000;
                red += r; green += g; blue += b;
                int chroma = Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
                if (chroma >= 35 && luma >= 35) {
                    colorRed += r; colorGreen += g; colorBlue += b; colorCount++;
                }
                minLuma = Math.min(minLuma, luma);
                maxLuma = Math.max(maxLuma, luma);
                // The darkest 30% of a cell is a useful outline signal. It
                // is accumulated after the first pass below using minLuma.
                count++;
            }
        }
        int averageRed = (int) (red / count);
        int averageGreen = (int) (green / count);
        int averageBlue = (int) (blue / count);
        int darkThreshold = Math.min(92, minLuma + Math.max(10, (maxLuma - minLuma) / 3));
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 255;
                int g = (rgb >> 8) & 255;
                int b = rgb & 255;
                int luma = (299 * r + 587 * g + 114 * b) / 1000;
                if (luma <= darkThreshold) {
                    darkRed += r; darkGreen += g; darkBlue += b; darkCount++;
                }
            }
        }
        int edgeRed = darkCount == 0 ? averageRed : (int) (darkRed / darkCount);
        int edgeGreen = darkCount == 0 ? averageGreen : (int) (darkGreen / darkCount);
        int edgeBlue = darkCount == 0 ? averageBlue : (int) (darkBlue / darkCount);
        int colorAverageRed = colorCount == 0 ? averageRed : (int) (colorRed / colorCount);
        int colorAverageGreen = colorCount == 0 ? averageGreen : (int) (colorGreen / colorCount);
        int colorAverageBlue = colorCount == 0 ? averageBlue : (int) (colorBlue / colorCount);
        return new Sample(averageRed, averageGreen, averageBlue, edgeRed, edgeGreen, edgeBlue,
            colorAverageRed, colorAverageGreen, colorAverageBlue,
            darkCount / (double) count, colorCount / (double) count, maxLuma - minLuma);
    }

    private List<PaletteColor> loadPalette(Long brandId) {
        Long actualBrandId = brandId == null ? 1L : brandId;
        return colorMapper.selectList(new LambdaQueryWrapper<BeadColorEntity>().eq(BeadColorEntity::getBrandId, actualBrandId).eq(BeadColorEntity::getStatus, 1).orderByAsc(BeadColorEntity::getSort)).stream().map(c -> {
            int r = c.getRgbR() == null ? 0 : c.getRgbR();
            int g = c.getRgbG() == null ? 0 : c.getRgbG();
            int b = c.getRgbB() == null ? 0 : c.getRgbB();
            return new PaletteColor(c.getId(), c.getColorCode(), c.getColorName(), c.getHex(), r, g, b,
                rgbToLab(r, g, b));
        }).toList();
    }

    private record PaletteColor(Long id, String code, String name, String hex, int r, int g, int b, Lab lab) {}
    private record Sample(int red, int green, int blue, int edgeRed, int edgeGreen, int edgeBlue,
                          int colorRed, int colorGreen, int colorBlue,
                          double darkRatio, double colorRatio, int edgeContrast) {}
    private record Lab(double l, double a, double b) {}

    private double chroma(Lab lab) {
        return Math.sqrt(lab.a() * lab.a() + lab.b() * lab.b());
    }

    private double labDistance(Lab left, Lab right) {
        double dl = left.l() - right.l();
        double da = left.a() - right.a();
        double db = left.b() - right.b();
        return dl * dl + da * da + db * db;
    }

    /** sRGB D65 -> CIE L*a*b*. */
    private Lab rgbToLab(int red, int green, int blue) {
        double r = srgbToLinear(red / 255.0);
        double g = srgbToLinear(green / 255.0);
        double b = srgbToLinear(blue / 255.0);
        double x = (r * 0.4124564 + g * 0.3575761 + b * 0.1804375) / 0.95047;
        double y = (r * 0.2126729 + g * 0.7151522 + b * 0.0721750);
        double z = (r * 0.0193339 + g * 0.1191920 + b * 0.9503041) / 1.08883;
        x = labPivot(x); y = labPivot(y); z = labPivot(z);
        return new Lab(116 * y - 16, 500 * (x - y), 200 * (y - z));
    }

    private double srgbToLinear(double value) {
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private double labPivot(double value) {
        double delta = 6.0 / 29.0;
        return value > delta * delta * delta ? Math.cbrt(value) : value / (3 * delta * delta) + 4.0 / 29.0;
    }

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

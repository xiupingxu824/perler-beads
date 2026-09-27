package com.perlerbeads.project;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.perlerbeads.common.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/projects")
@CrossOrigin(originPatterns = "*")
public class ProjectController {
    private final ProjectMapper mapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    public ProjectController(ProjectMapper mapper) { this.mapper = mapper; }

    @PostMapping
    public ApiResponse<ProjectEntity> save(@RequestBody SaveRequest request) {
        ProjectEntity entity = new ProjectEntity();

        entity.setId(request.id());
        entity.setUserId(request.userId());
        entity.setName(request.name());
        entity.setWidth(request.width());
        entity.setHeight(request.height());
        entity.setBrandId(request.brandId() == null ? 1L : request.brandId());
        entity.setSourceImageId(request.sourceImageId());
        entity.setMaxColors(request.maxColors() == null ? 16 : request.maxColors());
        entity.setPatternData(request.patternData());
        entity.setTotalBeads(request.width() * request.height()); entity.setStatus("DRAFT");
        entity.setDeleted(0); entity.setIsPublic(0); entity.setActualColorCount(0);
        if (entity.getId() == null || mapper.selectById(entity.getId()) == null)
        {
            entity.setId(UUID.randomUUID().toString());
            mapper.insert(entity);
        }
        else mapper.updateById(entity);
        return ApiResponse.ok(entity);
    }

    @GetMapping("/{id}")
    public ApiResponse<ProjectEntity> get(@PathVariable String id) { return ApiResponse.ok(mapper.selectById(id)); }

    @GetMapping
    public ApiResponse<List<ProjectEntity>> list(@RequestParam Long userId) { return ApiResponse.ok(mapper.selectList(new LambdaQueryWrapper<ProjectEntity>().eq(ProjectEntity::getUserId,userId).eq(ProjectEntity::getDeleted,0).orderByDesc(ProjectEntity::getUpdateTime))); }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable String id) { ProjectEntity p=mapper.selectById(id); if(p!=null){p.setDeleted(1);mapper.updateById(p);} return ApiResponse.ok(true); }

    @GetMapping(value="/{id}/export/json", produces="application/json")
    public ResponseEntity<byte[]> exportJson(@PathVariable String id) { ProjectEntity p=mapper.selectById(id); return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=perler-pattern.json").body((p==null?"{}":p.getPatternData()).getBytes(StandardCharsets.UTF_8)); }

    @GetMapping(value="/{id}/export/csv", produces="text/csv")
    public ResponseEntity<byte[]> exportCsv(@PathVariable String id) throws Exception { ProjectEntity project=mapper.selectById(id); if(project==null) return ResponseEntity.notFound().build(); PatternData data=objectMapper.readValue(project.getPatternData(),PatternData.class); StringBuilder out=new StringBuilder(); for(int row=0;row<data.height();row++){int start=row*data.width(),end=Math.min(start+data.width(),data.cells().size()); out.append(String.join(",",data.cells().subList(start,end))).append('\n');} return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=perler-pattern.csv").body(out.toString().getBytes(StandardCharsets.UTF_8)); }

    @GetMapping(value="/{id}/export/png", produces="image/png")
    public ResponseEntity<byte[]> exportPng(@PathVariable String id) throws Exception {
        ProjectEntity project=mapper.selectById(id);
        if(project==null) return ResponseEntity.notFound().build();
        PatternData data=objectMapper.readValue(project.getPatternData(),PatternData.class);
        Map<String,Integer> colorCounts=new LinkedHashMap<>();
        Map<String,Color> codeColors=new LinkedHashMap<>();
        if(data.codes()!=null) for(int i=0;i<data.codes().size();i++) {
            String code=data.codes().get(i);
            if(code==null||code.isBlank()) continue;
            colorCounts.merge(code,1,Integer::sum);
            if(i<data.cells().size()) { String hex=data.cells().get(i); if(hex!=null&&!hex.isBlank()) codeColors.putIfAbsent(code,Color.decode(hex)); }
        }
        List<Map.Entry<String,Integer>> legendEntries=new ArrayList<>(colorCounts.entrySet());
        legendEntries.sort(Map.Entry.comparingByKey(Comparator.comparing(this::codeSortKey)));
        int size=48, label=42, legendColumns=12, itemWidth=190;
        int legendRows=Math.max(1,(legendEntries.size()+legendColumns-1)/legendColumns);
        int legendHeight=106+legendRows*34;
        BufferedImage image=new BufferedImage(data.width()*size + label*2, data.height()*size + label*2 + legendHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setColor(Color.WHITE); g.fillRect(0, 0, image.getWidth(), image.getHeight());
        Font labelFont=new Font("Arial", Font.BOLD, 13);
        Font codeFont=new Font("Arial", Font.BOLD, 12);
        g.setFont(labelFont); g.setColor(Color.BLACK);
        FontMetrics labelMetrics=g.getFontMetrics();
        for(int c=0;c<data.width();c++) { String text=String.valueOf(c+1); int x=label+c*size+(size-labelMetrics.stringWidth(text))/2; g.drawString(text,x,label-12); g.drawString(text,x,label+data.height()*size+28); }
        for(int r=0;r<data.height();r++) { String text=String.valueOf(r+1); int y=label+r*size+(size-labelMetrics.getHeight())/2+labelMetrics.getAscent(); g.drawString(text,(label-labelMetrics.stringWidth(text))/2,y); g.drawString(text,label+data.width()*size+10,y); }
        g.setFont(codeFont);
        for(int r=0;r<data.height();r++) for(int c=0;c<data.width();c++) {
            int index=r*data.width()+c, x=label+c*size, y=label+r*size;
            String cell=index<data.cells().size()?data.cells().get(index):null;
            Color fill=cell==null||cell.isBlank()?Color.WHITE:Color.decode(cell);
            g.setColor(fill); g.fillRect(x,y,size,size);
            g.setColor(new Color(130,130,130)); g.drawRect(x,y,size,size);
            String code=data.codes()!=null&&index<data.codes().size()?data.codes().get(index):"";
            if(code!=null&&!code.isBlank()) { int luminance=(fill.getRed()*299+fill.getGreen()*587+fill.getBlue()*114)/1000; g.setColor(luminance<145?Color.WHITE:Color.DARK_GRAY); int tx=x+(size-g.getFontMetrics().stringWidth(code))/2; int ty=y+(size-g.getFontMetrics().getHeight())/2+g.getFontMetrics().getAscent(); g.drawString(code,tx,ty); }
        }
        int totalColors=colorCounts.size(), totalBeads=colorCounts.values().stream().mapToInt(Integer::intValue).sum();
        int legendY=label+data.height()*size+58;
        g.setFont(chineseFont(Font.BOLD,16)); g.setColor(Color.DARK_GRAY);
        g.drawString("颜色清单："+totalColors+" 种颜色 · 共 "+totalBeads+" 颗",label,legendY);
        g.setFont(chineseFont(Font.BOLD,13));
        int itemY=legendY+30, itemIndex=0;
        for(Map.Entry<String,Integer> entry:legendEntries) {
            int col=itemIndex%legendColumns, row=itemIndex/legendColumns;
            int x=label+col*itemWidth, y=itemY+row*34;
            Color swatch=codeColors.getOrDefault(entry.getKey(),Color.WHITE);
            g.setColor(swatch); g.fillRect(x,y-13,20,20);
            g.setColor(new Color(170,170,170)); g.drawRect(x,y-13,20,20);
            g.setColor(Color.DARK_GRAY); g.drawString(entry.getKey()+"  x"+entry.getValue(),x+28,y+3);
            itemIndex++;
        }
        g.dispose();
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        ImageIO.write(image,"png",output);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=perler-pattern.png").body(output.toByteArray()); }

    private String codeSortKey(String code) {
        if(code==null||code.isBlank()) return "ZZZZ999999";
        int index=code.length(); while(index>0&&Character.isDigit(code.charAt(index-1))) index--;
        String prefix=code.substring(0,index).toUpperCase(Locale.ROOT);
        String number=code.substring(index);
        try { return prefix+String.format(Locale.ROOT,"%06d",Integer.parseInt(number)); }
        catch(Exception ignored) { return code.toUpperCase(Locale.ROOT); }
    }

    private Font chineseFont(int style, int size) {
        String[] candidates={"Microsoft YaHei","SimSun","Noto Sans CJK SC","WenQuanYi Zen Hei","Dialog"};
        java.util.Set<String> available=new java.util.HashSet<>(java.util.Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for(String candidate:candidates) if(available.contains(candidate)) { Font font=new Font(candidate,style,size); if(font.canDisplay('颜') && font.canDisplay('色') && font.canDisplay('量')) return font; }
        return new Font("Dialog",style,size);
    }

    public record SaveRequest(String id, Long userId, @NotBlank String name, Integer width, Integer height, Long brandId, Integer maxColors, String sourceImageId, String patternData) {}
    public record PatternData(Integer width, Integer height, List<String> cells, List<String> codes) {}
}

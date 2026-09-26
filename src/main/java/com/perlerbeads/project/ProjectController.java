package com.perlerbeads.project;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.perlerbeads.common.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/projects")
@CrossOrigin(origins = "http://localhost:5173")
public class ProjectController {
    private final ProjectMapper mapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    public ProjectController(ProjectMapper mapper) { this.mapper = mapper; }

    @PostMapping
    public ApiResponse<ProjectEntity> save(@RequestBody SaveRequest request) {
        ProjectEntity entity = new ProjectEntity(); entity.setId(request.id()); entity.setUserId(request.userId()); entity.setName(request.name()); entity.setWidth(request.width()); entity.setHeight(request.height()); entity.setBrandId(request.brandId() == null ? 1L : request.brandId()); entity.setMaxColors(request.maxColors() == null ? 16 : request.maxColors()); entity.setPatternData(request.patternData()); entity.setTotalBeads(request.width() * request.height()); entity.setStatus("DRAFT"); entity.setDeleted(0); entity.setIsPublic(0); entity.setActualColorCount(0);
        if (entity.getId() == null || mapper.selectById(entity.getId()) == null) { entity.setId(null); mapper.insert(entity); } else mapper.updateById(entity);
        return ApiResponse.ok(entity);
    }

    @GetMapping("/{id}")
    public ApiResponse<ProjectEntity> get(@PathVariable Long id) { return ApiResponse.ok(mapper.selectById(id)); }

    @GetMapping
    public ApiResponse<List<ProjectEntity>> list(@RequestParam Long userId) { return ApiResponse.ok(mapper.selectList(new LambdaQueryWrapper<ProjectEntity>().eq(ProjectEntity::getUserId,userId).eq(ProjectEntity::getDeleted,0).orderByDesc(ProjectEntity::getUpdateTime))); }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Long id) { ProjectEntity p=mapper.selectById(id); if(p!=null){p.setDeleted(1);mapper.updateById(p);} return ApiResponse.ok(true); }

    @GetMapping(value="/{id}/export/json", produces="application/json")
    public ResponseEntity<byte[]> exportJson(@PathVariable Long id) { ProjectEntity p=mapper.selectById(id); return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=perler-pattern.json").body((p==null?"{}":p.getPatternData()).getBytes(StandardCharsets.UTF_8)); }

    @GetMapping(value="/{id}/export/csv", produces="text/csv")
    public ResponseEntity<byte[]> exportCsv(@PathVariable Long id) throws Exception { ProjectEntity project=mapper.selectById(id); if(project==null) return ResponseEntity.notFound().build(); PatternData data=objectMapper.readValue(project.getPatternData(),PatternData.class); StringBuilder out=new StringBuilder(); for(List<String> row:data.cells()) out.append(String.join(",",row)).append('\n'); return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=perler-pattern.csv").body(out.toString().getBytes(StandardCharsets.UTF_8)); }

    @GetMapping(value="/{id}/export/png", produces="image/png")
    public ResponseEntity<byte[]> exportPng(@PathVariable Long id) throws Exception { ProjectEntity project=mapper.selectById(id); if(project==null) return ResponseEntity.notFound().build(); PatternData data=objectMapper.readValue(project.getPatternData(),PatternData.class); int size=24; BufferedImage image=new BufferedImage(data.width()*size,data.height()*size,BufferedImage.TYPE_INT_ARGB); Graphics2D g=image.createGraphics(); for(int r=0;r<data.height();r++) for(int c=0;c<data.width();c++){g.setColor(Color.decode(data.cells().get(r).get(c)));g.fillRect(c*size,r*size,size,size);g.setColor(new Color(215,210,223));g.drawRect(c*size,r*size,size,size);} g.dispose(); ByteArrayOutputStream output=new ByteArrayOutputStream(); ImageIO.write(image,"png",output); return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=perler-pattern.png").body(output.toByteArray()); }

    public record SaveRequest(Long id, Long userId, @NotBlank String name, Integer width, Integer height, Long brandId, Integer maxColors, String patternData) {}
    public record PatternData(Integer width, Integer height, List<List<String>> cells) {}
}

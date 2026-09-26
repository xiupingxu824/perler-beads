package com.perlerbeads.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.perlerbeads.common.ApiResponse;
import com.perlerbeads.user.UserEntity;
import com.perlerbeads.user.UserMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:5173")
public class AuthController {
    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthController(UserMapper userMapper) { this.userMapper = userMapper; }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        UserEntity user = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
            .eq(UserEntity::getUsername, request.username()).eq(UserEntity::getDeleted, 0).last("LIMIT 1"));
        if (user == null || user.getStatus() != 1 || !matches(request.password(), user.getPassword())) {
            return ApiResponse.fail("账号或密码错误");
        }
        // 第一版先返回用户信息；接入 JWT 时在这里生成 token。
        return ApiResponse.ok(new LoginResponse(user.getId(), user.getUsername(), user.getNickname(), user.getRole(), "demo-session"));
    }

    private boolean matches(String raw, String stored) {
        return stored != null && (stored.startsWith("$2") ? passwordEncoder.matches(raw, stored) : stored.equals(raw));
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record LoginResponse(Long id, String username, String nickname, String role, String token) {}
}

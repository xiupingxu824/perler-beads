package com.perlerbeads.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@Service
public class JwtService {
    private static final String HMAC = "HmacSHA256";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final byte[] secret;
    private final long expirationSeconds;

    public JwtService(@Value("${perler.jwt.secret}") String secret,
                      @Value("${perler.jwt.expiration-seconds:3600}") long expirationSeconds) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("perler.jwt.secret 至少需要 32 个字符");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.expirationSeconds = expirationSeconds;
    }

    public String createToken(Long id, String username, String role) {
        long now = Instant.now().getEpochSecond();
        long exp = now + expirationSeconds;
        String header = encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = encodeJson(Map.of("sub", String.valueOf(id), "username", username, "role", role == null ? "USER" : role, "iat", now, "exp", exp));
        String content = header + "." + payload;
        return content + "." + sign(content);
    }

    public JsonNode verify(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3 || !constantTimeEquals(sign(parts[0] + "." + parts[1]), parts[2])) return null;
            JsonNode payload = objectMapper.readTree(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
            if (!payload.has("exp") || payload.get("exp").asLong() <= Instant.now().getEpochSecond()) return null;
            return payload;
        } catch (Exception ignored) { return null; }
    }

    private String encode(String value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
    private String encodeJson(Object value) { try { return encode(objectMapper.writeValueAsString(value)); } catch (Exception e) { throw new IllegalStateException(e); } }
    private String sign(String value) { try { Mac mac = Mac.getInstance(HMAC); mac.init(new SecretKeySpec(secret, HMAC)); return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException("JWT 签名失败", e); } }
    private boolean constantTimeEquals(String a, String b) { return java.security.MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8)); }
}

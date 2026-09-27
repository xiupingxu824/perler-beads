package com.perlerbeads.auth;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) { this.jwtService = jwtService; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod()) || "/api/auth/login".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) { unauthorized(request, response, "未登录或登录已过期"); return; }
        JsonNode payload = jwtService.verify(authorization.substring(7).trim());
        if (payload == null) { unauthorized(request, response, "登录已过期，请重新登录"); return; }
        request.setAttribute("jwtUserId", payload.path("sub").asText());
        request.setAttribute("jwtUsername", payload.path("username").asText());
        chain.doFilter(request, response);
    }

    private void unauthorized(HttpServletRequest request, HttpServletResponse response, String message) throws IOException {
        String origin = request.getHeader("Origin");
        if (origin != null && !origin.isBlank()) { response.setHeader("Access-Control-Allow-Origin", origin); response.setHeader("Vary", "Origin"); }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"success\":false,\"data\":null,\"message\":\"" + message + "\"}");
    }
}

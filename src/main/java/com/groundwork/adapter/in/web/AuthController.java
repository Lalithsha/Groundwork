package com.groundwork.adapter.in.web;

import com.groundwork.config.JwtUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    public AuthController(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder, JwtUtils jwtUtils) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
    }

    public record RegisterRequest(String email, String password, String role) {}
    public record LoginRequest(String email, String password) {}

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        String role = request.role() != null ? request.role() : "USER";
        String encodedPass = passwordEncoder.encode(request.password());
        UUID userId = UUID.randomUUID();

        try {
            jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, role) VALUES (?, ?, ?, ?)",
                userId, request.email(), encodedPass, role
            );
            // Create subscription default tier (Phase 7)
            jdbcTemplate.update(
                "INSERT INTO subscriptions (user_id, tier) VALUES (?, 'FREE')",
                userId
            );
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email already exists"));
        }

        String token = jwtUtils.generateToken(request.email(), role);
        return ResponseEntity.ok(Map.of("message", "User registered successfully", "userId", userId, "token", token));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        var users = jdbcTemplate.queryForList("SELECT id, password_hash, role FROM users WHERE email = ?", request.email());
        if (users.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }

        var user = users.get(0);
        String passHash = (String) user.get("password_hash");
        if (!passwordEncoder.matches(request.password(), passHash)) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }

        String role = (String) user.get("role");
        String token = jwtUtils.generateToken(request.email(), role);
        return ResponseEntity.ok(Map.of("token", token, "email", request.email(), "role", role));
    }
}

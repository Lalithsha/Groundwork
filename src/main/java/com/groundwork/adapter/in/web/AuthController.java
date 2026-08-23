package com.groundwork.adapter.in.web;

import com.groundwork.application.Hashing;
import com.groundwork.config.JwtUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final long refreshExpirationMs;
    private final long accessExpirationMs;

    public AuthController(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder, JwtUtils jwtUtils,
            @org.springframework.beans.factory.annotation.Value("${groundwork.jwt.refresh-expiration-ms:604800000}") long refreshExpirationMs,
            @org.springframework.beans.factory.annotation.Value("${groundwork.jwt.expiration-ms:900000}") long accessExpirationMs) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.refreshExpirationMs = refreshExpirationMs;
        this.accessExpirationMs = accessExpirationMs;
    }

    public record RegisterRequest(@Email @NotBlank String email, @Size(min = 12, max = 128) String password, String role) {}
    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}
    public record RefreshRequest(@NotBlank String refreshToken) {}

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        UUID userId = UUID.randomUUID();
        String email = normalizeEmail(request.email());
        try {
            jdbcTemplate.update("INSERT INTO users (id, email, password_hash, role) VALUES (?, ?, ?, 'USER')",
                userId, email, passwordEncoder.encode(request.password()));
            jdbcTemplate.update("INSERT INTO subscriptions (user_id, tier) VALUES (?, 'FREE')", userId);
        } catch (DataIntegrityViolationException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "An account with that email already exists"));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(issueTokens(userId, email, "USER"));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        String email = normalizeEmail(request.email());
        var users = jdbcTemplate.queryForList("SELECT id, password_hash, role FROM users WHERE LOWER(email) = LOWER(?)", email);
        if (users.isEmpty() || !passwordEncoder.matches(request.password(), (String) users.getFirst().get("password_hash"))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid credentials"));
        }
        Map<String, Object> user = users.getFirst();
        return ResponseEntity.ok(issueTokens((UUID) user.get("id"), email, (String) user.get("role")));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@Valid @RequestBody RefreshRequest request) {
        String hash = Hashing.sha256(request.refreshToken());
        var tokens = jdbcTemplate.queryForList("""
            SELECT token.id, account.id AS user_id, account.email, account.role
            FROM refresh_tokens token JOIN users account ON account.id = token.user_id
            WHERE token.token_hash = ? AND token.revoked = false AND token.expires_at > now()
            """, hash);
        if (tokens.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid refresh token"));
        Map<String, Object> token = tokens.getFirst();
        jdbcTemplate.update("UPDATE refresh_tokens SET revoked = true WHERE id = ?", token.get("id"));
        return ResponseEntity.ok(issueTokens((UUID) token.get("user_id"), (String) token.get("email"), (String) token.get("role")));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        jdbcTemplate.update("UPDATE refresh_tokens SET revoked = true WHERE token_hash = ?", Hashing.sha256(request.refreshToken()));
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> issueTokens(UUID userId, String email, String role) {
        jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ? AND (revoked = true OR expires_at <= now())", userId);
        String refreshToken = randomToken();
        jdbcTemplate.update("INSERT INTO refresh_tokens (user_id, token_hash, expires_at) VALUES (?, ?, ?)",
            userId, Hashing.sha256(refreshToken), java.sql.Timestamp.from(Instant.now().plusMillis(refreshExpirationMs)));
        String accessToken = jwtUtils.generateToken(email, role);
        return Map.of(
            "token", accessToken,
            "accessToken", accessToken,
            "refreshToken", refreshToken,
            "tokenType", "Bearer",
            "expiresInMs", accessExpirationMs,
            "email", email,
            "role", role,
            "userId", userId
        );
    }

    private String randomToken() {
        byte[] bytes = new byte[48];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String normalizeEmail(String email) { return email.strip().toLowerCase(java.util.Locale.ROOT); }
}

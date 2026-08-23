package com.groundwork.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtils {

    @Value("${groundwork.jwt.secret:supersecretkeyforgroundworkjwttokengeneration12345}")
    private String jwtSecret;

    @Value("${groundwork.jwt.expiration-ms:900000}")
    private long jwtExpirationMs;

    @Value("${groundwork.jwt.issuer:groundwork}")
    private String issuer;

    @Value("${groundwork.jwt.audience:groundwork-api}")
    private String audience;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String email, String role) {
        return Jwts.builder()
            .subject(email)
            .claim("role", role)
            .issuer(issuer)
            .audience().add(audience).and()
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
            .signWith(getSigningKey())
            .compact();
    }

    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = parseClaims(token);
            return issuer.equals(claims.getIssuer()) && claims.getAudience() != null && claims.getAudience().contains(audience);
        } catch (Exception e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}

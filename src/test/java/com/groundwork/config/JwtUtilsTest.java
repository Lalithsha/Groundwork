package com.groundwork.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilsTest {
    @Test
    void validatesSignatureIssuerAudienceAndClaims() {
        JwtUtils jwt = configured("issuer-a", "audience-a");
        String token = jwt.generateToken("person@example.com", "USER");

        assertThat(jwt.validateToken(token)).isTrue();
        assertThat(jwt.extractEmail(token)).isEqualTo("person@example.com");
        assertThat(jwt.extractRole(token)).isEqualTo("USER");
        assertThat(configured("issuer-b", "audience-a").validateToken(token)).isFalse();
        assertThat(configured("issuer-a", "audience-b").validateToken(token)).isFalse();
    }

    private JwtUtils configured(String issuer, String audience) {
        JwtUtils jwt = new JwtUtils();
        ReflectionTestUtils.setField(jwt, "jwtSecret", "test-secret-that-is-deliberately-longer-than-forty-eight-characters");
        ReflectionTestUtils.setField(jwt, "jwtExpirationMs", 60_000L);
        ReflectionTestUtils.setField(jwt, "issuer", issuer);
        ReflectionTestUtils.setField(jwt, "audience", audience);
        return jwt;
    }
}

package com.groundwork.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.groundwork.application.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class AuditInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(AuditInterceptor.class);
    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private final JdbcTemplate jdbcTemplate;
    private final CurrentUser currentUser;
    private final ObjectMapper objectMapper;

    public AuditInterceptor(JdbcTemplate jdbcTemplate, CurrentUser currentUser, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.currentUser = currentUser;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
            Exception exception) {
        if (!MUTATING_METHODS.contains(request.getMethod()) || request.getRequestURI().startsWith("/api/auth/login") ||
                request.getRequestURI().startsWith("/api/auth/refresh")) return;
        UUID workspaceId = parseUuid(request.getParameter("workspaceId"));
        UUID actorId = currentUser.email().flatMap(email -> jdbcTemplate.query(
            "SELECT id FROM users WHERE LOWER(email) = LOWER(?)", (rs, rowNum) -> rs.getObject(1, UUID.class), email)
            .stream().findFirst()).orElse(null);
        try {
            String metadata = objectMapper.writeValueAsString(Map.of(
                "method", request.getMethod(), "path", request.getRequestURI(), "status", response.getStatus()));
            jdbcTemplate.update("""
                INSERT INTO audit_events (actor_user_id, workspace_id, event_type, request_id, metadata)
                VALUES (?, ?, ?, ?, CAST(? AS jsonb))
                """, actorId, workspaceId, "HTTP_MUTATION", MDC.get("requestId"), metadata);
        } catch (JsonProcessingException | RuntimeException failure) {
            log.warn("Could not persist audit event for {} {}", request.getMethod(), request.getRequestURI());
        }
    }

    private UUID parseUuid(String value) {
        try { return value == null ? null : UUID.fromString(value); }
        catch (IllegalArgumentException ignored) { return null; }
    }
}

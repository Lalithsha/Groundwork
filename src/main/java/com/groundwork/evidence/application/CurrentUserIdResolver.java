package com.groundwork.evidence.application;

import com.groundwork.application.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class CurrentUserIdResolver {
    private final CurrentUser currentUser;
    private final JdbcTemplate jdbc;

    public CurrentUserIdResolver(CurrentUser currentUser, JdbcTemplate jdbc) {
        this.currentUser = currentUser;
        this.jdbc = jdbc;
    }

    public Optional<UUID> optional() {
        return currentUser.email().flatMap(email -> jdbc.queryForList(
            "SELECT id FROM users WHERE LOWER(email) = LOWER(?)", UUID.class, email).stream().findFirst());
    }
}

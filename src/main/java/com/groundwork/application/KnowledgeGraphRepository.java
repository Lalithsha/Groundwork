package com.groundwork.application;

import com.groundwork.domain.model.GraphEntity;
import com.groundwork.domain.model.GraphRelationship;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class KnowledgeGraphRepository {

    private final JdbcTemplate jdbcTemplate;

    public KnowledgeGraphRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<GraphEntity> entityRowMapper = (rs, rowNum) -> new GraphEntity(
        UUID.fromString(rs.getString("id")),
        rs.getString("workspace_id") != null ? UUID.fromString(rs.getString("workspace_id")) : null,
        rs.getString("name"),
        rs.getString("entity_type"),
        rs.getString("description"),
        rs.getTimestamp("created_at").toInstant()
    );

    private final RowMapper<GraphRelationship> relationshipRowMapper = (rs, rowNum) -> new GraphRelationship(
        UUID.fromString(rs.getString("id")),
        rs.getString("workspace_id") != null ? UUID.fromString(rs.getString("workspace_id")) : null,
        UUID.fromString(rs.getString("source_entity_id")),
        UUID.fromString(rs.getString("target_entity_id")),
        rs.getString("relationship_type"),
        rs.getString("description"),
        rs.getTimestamp("created_at").toInstant()
    );

    public GraphEntity saveEntity(UUID workspaceId, String name, String entityType, String description) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String sql = """
            INSERT INTO graph_entities (id, workspace_id, name, entity_type, description, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        jdbcTemplate.update(sql, id, workspaceId, name, entityType, description, java.sql.Timestamp.from(now));
        return new GraphEntity(id, workspaceId, name, entityType, description, now);
    }

    public GraphRelationship saveRelationship(UUID workspaceId, UUID sourceEntityId, UUID targetEntityId, String relationshipType, String description) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String sql = """
            INSERT INTO graph_relationships (id, workspace_id, source_entity_id, target_entity_id, relationship_type, description, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        jdbcTemplate.update(sql, id, workspaceId, sourceEntityId, targetEntityId, relationshipType, description, java.sql.Timestamp.from(now));
        return new GraphRelationship(id, workspaceId, sourceEntityId, targetEntityId, relationshipType, description, now);
    }

    public List<GraphEntity> findEntitiesByWorkspace(UUID workspaceId) {
        if (workspaceId != null) {
            String sql = "SELECT id, workspace_id, name, entity_type, description, created_at FROM graph_entities WHERE workspace_id = ? ORDER BY created_at DESC";
            return jdbcTemplate.query(sql, entityRowMapper, workspaceId);
        }
        return findAllEntities();
    }

    public List<GraphEntity> findAllEntities() {
        String sql = "SELECT id, workspace_id, name, entity_type, description, created_at FROM graph_entities ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, entityRowMapper);
    }

    public List<GraphRelationship> findRelationshipsByWorkspace(UUID workspaceId) {
        if (workspaceId != null) {
            String sql = "SELECT id, workspace_id, source_entity_id, target_entity_id, relationship_type, description, created_at FROM graph_relationships WHERE workspace_id = ? ORDER BY created_at DESC";
            return jdbcTemplate.query(sql, relationshipRowMapper, workspaceId);
        }
        return findAllRelationships();
    }

    public List<GraphRelationship> findAllRelationships() {
        String sql = "SELECT id, workspace_id, source_entity_id, target_entity_id, relationship_type, description, created_at FROM graph_relationships ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, relationshipRowMapper);
    }

    public Optional<GraphEntity> findEntityByName(String name, UUID workspaceId) {
        if (workspaceId != null) {
            String sql = "SELECT id, workspace_id, name, entity_type, description, created_at FROM graph_entities WHERE LOWER(name) = LOWER(?) AND workspace_id = ?";
            List<GraphEntity> list = jdbcTemplate.query(sql, entityRowMapper, name.trim(), workspaceId);
            return list.stream().findFirst();
        }
        String sql = "SELECT id, workspace_id, name, entity_type, description, created_at FROM graph_entities WHERE LOWER(name) = LOWER(?)";
        List<GraphEntity> list = jdbcTemplate.query(sql, entityRowMapper, name.trim());
        return list.stream().findFirst();
    }

    public void clearGraph(UUID workspaceId) {
        if (workspaceId != null) {
            jdbcTemplate.update("DELETE FROM graph_relationships WHERE workspace_id = ?", workspaceId);
            jdbcTemplate.update("DELETE FROM graph_entities WHERE workspace_id = ?", workspaceId);
        } else {
            jdbcTemplate.update("DELETE FROM graph_relationships");
            jdbcTemplate.update("DELETE FROM graph_entities");
        }
    }
}

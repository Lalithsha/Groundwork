package com.groundwork.evidence.application.port.out;

import com.groundwork.evidence.domain.ConnectorConnection;

import java.util.List;
import java.util.Map;

public interface KnowledgeSourcePort {
    boolean supports(String provider);
    List<SourceArtifact> fetch(ConnectorConnection connection);

    record SourceArtifact(String externalId, String artifactType, String title, String canonicalUrl,
                          String sourceVersion, String content, Map<String, Object> metadata,
                          Map<String, Object> sourceAcl) {}
}

package com.groundwork.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groundwork.application.DocumentRepository;
import com.groundwork.application.KnowledgeGraphRepository;
import com.groundwork.application.StructuredExtractionService;
import com.groundwork.application.WorkspaceAccessService;
import com.groundwork.domain.model.DocumentChunk;
import com.groundwork.domain.model.GraphEntity;
import com.groundwork.domain.model.GraphRelationship;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/graph")
public class KnowledgeGraphController {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeGraphController.class);

    private final KnowledgeGraphRepository graphRepository;
    private final DocumentRepository documentRepository;
    private final StructuredExtractionService extractionService;
    private final ObjectMapper objectMapper;
    private final WorkspaceAccessService access;

    public KnowledgeGraphController(
            KnowledgeGraphRepository graphRepository,
            DocumentRepository documentRepository,
            StructuredExtractionService extractionService,
            ObjectMapper objectMapper, WorkspaceAccessService access) {
        this.graphRepository = graphRepository;
        this.documentRepository = documentRepository;
        this.extractionService = extractionService;
        this.objectMapper = objectMapper;
        this.access = access;
    }

    public record ExtractGraphRequest(
        UUID workspaceId,
        String documentTitle,
        String rawText
    ) {}

    public record GraphResponse(
        List<GraphEntity> entities,
        List<GraphRelationship> relationships
    ) {}

    public record CreateEntityRequest(
        UUID workspaceId,
        String name,
        String entityType,
        String description
    ) {}

    public record CreateRelationshipRequest(
        UUID workspaceId,
        UUID sourceEntityId,
        UUID targetEntityId,
        String relationshipType,
        String description
    ) {}

    @PostMapping("/extract")
    public ResponseEntity<GraphResponse> extractGraph(@RequestBody ExtractGraphRequest request) {
        access.requireEditor(request.workspaceId());
        String textContent = request.rawText();
        String title = request.documentTitle();

        if ((textContent == null || textContent.isBlank()) && title != null && !title.isBlank()) {
            List<DocumentChunk> chunks = documentRepository.findByTitle(title, request.workspaceId());
            if (!chunks.isEmpty()) {
                textContent = chunks.stream().map(DocumentChunk::content).collect(Collectors.joining("\n\n"));
            }
        }

        if (textContent == null || textContent.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String graphJson = extractionService.extractKnowledgeGraph(title != null ? title : "Document", textContent);
        List<GraphEntity> savedEntities = new ArrayList<>();
        List<GraphRelationship> savedRelationships = new ArrayList<>();

        try {
            var graph = objectMapper.readTree(graphJson);
            var entitiesList = graph.path("entities");
            var relsList = graph.path("relationships");

            Map<String, GraphEntity> entityByName = new HashMap<>();

            if (entitiesList.isArray()) {
                for (var entityNode : entitiesList) {
                    String name = entityNode.path("name").asText(null);
                    String type = entityNode.path("entityType").asText(null);
                    String desc = entityNode.path("description").asText(null);
                    if (name != null && !name.isBlank()) {
                        GraphEntity entity = graphRepository.findEntityByName(name, request.workspaceId())
                            .orElseGet(() -> graphRepository.saveEntity(
                                request.workspaceId(),
                                name,
                                type != null ? type : "CONCEPT",
                                desc
                            ));
                        entityByName.put(name.toLowerCase(), entity);
                        savedEntities.add(entity);
                    }
                }
            }

            if (relsList.isArray()) {
                for (var relationshipNode : relsList) {
                    String srcName = relationshipNode.path("source").asText(null);
                    String tgtName = relationshipNode.path("target").asText(null);
                    String relType = relationshipNode.path("relationshipType").asText(null);
                    String desc = relationshipNode.path("description").asText(null);

                    GraphEntity srcEntity = srcName != null ? entityByName.get(srcName.toLowerCase()) : null;
                    GraphEntity tgtEntity = tgtName != null ? entityByName.get(tgtName.toLowerCase()) : null;

                    if (srcEntity != null && tgtEntity != null) {
                        GraphRelationship rel = graphRepository.saveRelationship(
                            request.workspaceId(),
                            srcEntity.id(),
                            tgtEntity.id(),
                            relType != null ? relType : "RELATED_TO",
                            desc
                        );
                        savedRelationships.add(rel);
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Could not parse structured graph output: {}", ex.getMessage());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(new GraphResponse(savedEntities, savedRelationships));
    }

    @GetMapping
    public ResponseEntity<GraphResponse> getGraph(@RequestParam(required = false) UUID workspaceId) {
        access.requireViewer(workspaceId);
        List<GraphEntity> entities = graphRepository.findEntitiesByWorkspace(workspaceId);
        List<GraphRelationship> relationships = graphRepository.findRelationshipsByWorkspace(workspaceId);
        return ResponseEntity.ok(new GraphResponse(entities, relationships));
    }

    @PostMapping("/entities")
    public ResponseEntity<GraphEntity> createEntity(@RequestBody CreateEntityRequest request) {
        access.requireEditor(request.workspaceId());
        if (request.name() == null || request.name().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        GraphEntity entity = graphRepository.saveEntity(
            request.workspaceId(),
            request.name().trim(),
            request.entityType() != null ? request.entityType() : "CONCEPT",
            request.description()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(entity);
    }

    @PostMapping("/relationships")
    public ResponseEntity<GraphRelationship> createRelationship(@RequestBody CreateRelationshipRequest request) {
        access.requireEditor(request.workspaceId());
        if (request.sourceEntityId() == null || request.targetEntityId() == null) {
            return ResponseEntity.badRequest().build();
        }
        GraphRelationship rel = graphRepository.saveRelationship(
            request.workspaceId(),
            request.sourceEntityId(),
            request.targetEntityId(),
            request.relationshipType() != null ? request.relationshipType() : "RELATED_TO",
            request.description()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(rel);
    }

    @DeleteMapping
    public ResponseEntity<Void> clearGraph(@RequestParam(required = false) UUID workspaceId) {
        access.requireEditor(workspaceId);
        graphRepository.clearGraph(workspaceId);
        return ResponseEntity.noContent().build();
    }
}

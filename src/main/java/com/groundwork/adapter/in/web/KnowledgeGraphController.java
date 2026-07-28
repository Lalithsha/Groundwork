package com.groundwork.adapter.in.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.groundwork.application.DocumentRepository;
import com.groundwork.application.KnowledgeGraphRepository;
import com.groundwork.application.StructuredExtractionService;
import com.groundwork.domain.model.DocumentChunk;
import com.groundwork.domain.model.GraphEntity;
import com.groundwork.domain.model.GraphRelationship;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/graph")
public class KnowledgeGraphController {

    private final KnowledgeGraphRepository graphRepository;
    private final DocumentRepository documentRepository;
    private final StructuredExtractionService extractionService;
    private final ObjectMapper objectMapper;

    public KnowledgeGraphController(
            KnowledgeGraphRepository graphRepository,
            DocumentRepository documentRepository,
            StructuredExtractionService extractionService,
            ObjectMapper objectMapper) {
        this.graphRepository = graphRepository;
        this.documentRepository = documentRepository;
        this.extractionService = extractionService;
        this.objectMapper = objectMapper;
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
        String textContent = request.rawText();
        String title = request.documentTitle();

        if ((textContent == null || textContent.isBlank()) && title != null && !title.isBlank()) {
            List<DocumentChunk> chunks = documentRepository.findByTitle(title);
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
            Map<String, Object> map = objectMapper.readValue(graphJson, new TypeReference<>() {});
            List<Map<String, String>> entitiesList = (List<Map<String, String>>) map.get("entities");
            List<Map<String, String>> relsList = (List<Map<String, String>>) map.get("relationships");

            Map<String, GraphEntity> entityByName = new HashMap<>();

            if (entitiesList != null) {
                for (Map<String, String> e : entitiesList) {
                    String name = e.get("name");
                    String type = e.get("entityType");
                    String desc = e.get("description");
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

            if (relsList != null) {
                for (Map<String, String> r : relsList) {
                    String srcName = r.get("source");
                    String tgtName = r.get("target");
                    String relType = r.get("relationshipType");
                    String desc = r.get("description");

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
            System.err.println("Error parsing extracted graph JSON: " + ex.getMessage());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(new GraphResponse(savedEntities, savedRelationships));
    }

    @GetMapping
    public ResponseEntity<GraphResponse> getGraph(@RequestParam(required = false) UUID workspaceId) {
        List<GraphEntity> entities = graphRepository.findEntitiesByWorkspace(workspaceId);
        List<GraphRelationship> relationships = graphRepository.findRelationshipsByWorkspace(workspaceId);
        return ResponseEntity.ok(new GraphResponse(entities, relationships));
    }

    @PostMapping("/entities")
    public ResponseEntity<GraphEntity> createEntity(@RequestBody CreateEntityRequest request) {
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
        graphRepository.clearGraph(workspaceId);
        return ResponseEntity.noContent().build();
    }
}

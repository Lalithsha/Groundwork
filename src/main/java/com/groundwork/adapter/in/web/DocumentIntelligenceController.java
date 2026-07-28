package com.groundwork.adapter.in.web;

import com.groundwork.application.DocumentRepository;
import com.groundwork.application.KnowledgeArtifactRepository;
import com.groundwork.application.StructuredExtractionService;
import com.groundwork.domain.model.DocumentChunk;
import com.groundwork.domain.model.KnowledgeArtifact;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/artifacts")
public class DocumentIntelligenceController {

    private final KnowledgeArtifactRepository artifactRepository;
    private final DocumentRepository documentRepository;
    private final StructuredExtractionService extractionService;

    public DocumentIntelligenceController(
            KnowledgeArtifactRepository artifactRepository,
            DocumentRepository documentRepository,
            StructuredExtractionService extractionService) {
        this.artifactRepository = artifactRepository;
        this.documentRepository = documentRepository;
        this.extractionService = extractionService;
    }

    public record ExtractArtifactRequest(
        UUID workspaceId,
        String documentTitle,
        String artifactType,
        String rawText
    ) {}

    @PostMapping("/extract")
    public ResponseEntity<KnowledgeArtifact> extractArtifact(@RequestBody ExtractArtifactRequest request) {
        String type = request.artifactType() != null && !request.artifactType().isBlank()
                ? request.artifactType()
                : "brief";

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

        if (title == null || title.isBlank()) {
            title = "Extracted " + type + " Artifact";
        }

        String structuredJson = extractionService.extractKnowledgeArtifact(title, type, textContent);
        KnowledgeArtifact artifact = artifactRepository.save(
            request.workspaceId(),
            title,
            type,
            textContent.length() > 5000 ? textContent.substring(0, 5000) + "..." : textContent,
            structuredJson
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(artifact);
    }

    @GetMapping
    public ResponseEntity<List<KnowledgeArtifact>> getArtifacts(@RequestParam(required = false) UUID workspaceId) {
        if (workspaceId != null) {
            return ResponseEntity.ok(artifactRepository.findByWorkspaceId(workspaceId));
        }
        return ResponseEntity.ok(artifactRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<KnowledgeArtifact> getArtifactById(@PathVariable UUID id) {
        return artifactRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteArtifact(@PathVariable UUID id) {
        boolean deleted = artifactRepository.deleteById(id);
        if (deleted) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}

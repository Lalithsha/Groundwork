package com.groundwork.adapter.in.web;

import com.groundwork.application.ComparisonRepository;
import com.groundwork.application.DocumentRepository;
import com.groundwork.application.StructuredExtractionService;
import com.groundwork.domain.model.DocumentChunk;
import com.groundwork.domain.model.DocumentComparison;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/compare")
public class DocumentComparisonController {

    private final ComparisonRepository comparisonRepository;
    private final DocumentRepository documentRepository;
    private final StructuredExtractionService extractionService;

    public DocumentComparisonController(
            ComparisonRepository comparisonRepository,
            DocumentRepository documentRepository,
            StructuredExtractionService extractionService) {
        this.comparisonRepository = comparisonRepository;
        this.documentRepository = documentRepository;
        this.extractionService = extractionService;
    }

    public record CompareRequest(
        UUID workspaceId,
        String docTitleA,
        String docTitleB,
        String textA,
        String textB
    ) {}

    @PostMapping
    public ResponseEntity<DocumentComparison> compareDocuments(@RequestBody CompareRequest request) {
        String titleA = request.docTitleA() != null ? request.docTitleA() : "Document A";
        String titleB = request.docTitleB() != null ? request.docTitleB() : "Document B";

        String contentA = request.textA();
        if ((contentA == null || contentA.isBlank()) && request.docTitleA() != null) {
            List<DocumentChunk> chunksA = documentRepository.findByTitle(request.docTitleA());
            if (!chunksA.isEmpty()) {
                contentA = chunksA.stream().map(DocumentChunk::content).collect(Collectors.joining("\n\n"));
            }
        }

        String contentB = request.textB();
        if ((contentB == null || contentB.isBlank()) && request.docTitleB() != null) {
            List<DocumentChunk> chunksB = documentRepository.findByTitle(request.docTitleB());
            if (!chunksB.isEmpty()) {
                contentB = chunksB.stream().map(DocumentChunk::content).collect(Collectors.joining("\n\n"));
            }
        }

        if (contentA == null || contentA.isBlank() || contentB == null || contentB.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String diffSummaryJson = extractionService.compareDocuments(titleA, contentA, titleB, contentB);
        String overview = "Compared " + titleA + " vs " + titleB;

        DocumentComparison comparison = comparisonRepository.save(
            request.workspaceId(),
            titleA,
            titleB,
            overview,
            diffSummaryJson
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(comparison);
    }

    @GetMapping
    public ResponseEntity<List<DocumentComparison>> getComparisons(@RequestParam(required = false) UUID workspaceId) {
        if (workspaceId != null) {
            return ResponseEntity.ok(comparisonRepository.findByWorkspaceId(workspaceId));
        }
        return ResponseEntity.ok(comparisonRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentComparison> getComparisonById(@PathVariable UUID id) {
        return comparisonRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}

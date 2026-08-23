package com.groundwork.adapter.in.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.groundwork.application.DocumentRepository;
import com.groundwork.application.ReviewReportRepository;
import com.groundwork.application.StructuredExtractionService;
import com.groundwork.application.WorkspaceAccessService;
import com.groundwork.domain.model.DecisionLogEntry;
import com.groundwork.domain.model.DocumentChunk;
import com.groundwork.domain.model.ReviewReport;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/review")
public class AiReviewerController {
    private static final Logger log = LoggerFactory.getLogger(AiReviewerController.class);

    private final ReviewReportRepository reviewReportRepository;
    private final DocumentRepository documentRepository;
    private final StructuredExtractionService extractionService;
    private final ObjectMapper objectMapper;
    private final WorkspaceAccessService access;

    public AiReviewerController(
            ReviewReportRepository reviewReportRepository,
            DocumentRepository documentRepository,
            StructuredExtractionService extractionService,
            ObjectMapper objectMapper, WorkspaceAccessService access) {
        this.reviewReportRepository = reviewReportRepository;
        this.documentRepository = documentRepository;
        this.extractionService = extractionService;
        this.objectMapper = objectMapper;
        this.access = access;
    }

    public record ReviewRequest(
        UUID workspaceId,
        String documentTitle,
        String content
    ) {}

    @PostMapping
    public ResponseEntity<ReviewReport> reviewContent(@RequestBody ReviewRequest request) {
        access.requireEditor(request.workspaceId());
        String textContent = request.content();
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

        if (title == null || title.isBlank()) {
            title = "AI Review Report";
        }

        String reportJson = extractionService.performAiReview(title, textContent);

        String status = "NEEDS_REVISION";
        Double score = 0.0;
        String feedback = "Review did not produce a verified conclusion.";

        try {
            Map<String, Object> map = objectMapper.readValue(reportJson, new TypeReference<>() {});
            if (map.containsKey("status") && map.get("status") != null) {
                status = map.get("status").toString();
            }
            if (map.containsKey("qualityScore") && map.get("qualityScore") != null) {
                score = Double.valueOf(map.get("qualityScore").toString());
            }
            if (map.containsKey("feedback") && map.get("feedback") != null) {
                feedback = map.get("feedback").toString();
            }

            if (map.containsKey("decisions") && map.get("decisions") instanceof List<?> decList) {
                for (Object item : decList) {
                    if (item instanceof Map<?, ?> decMap) {
                        String decStr = decMap.get("decision") != null ? decMap.get("decision").toString() : "Review Assessment";
                        String ratStr = decMap.get("rationale") != null ? decMap.get("rationale").toString() : feedback;
                        String actStr = decMap.get("actor") != null ? decMap.get("actor").toString() : "AI_REVIEWER";
                        reviewReportRepository.saveDecision(request.workspaceId(), decStr, ratStr, actStr);
                    }
                }
            } else {
                reviewReportRepository.saveDecision(
                    request.workspaceId(),
                    "Review Outcome: " + status,
                    feedback,
                    "AI_REVIEWER"
                );
            }
        } catch (Exception ex) {
            log.warn("Could not parse structured review output: {}", ex.getMessage());
        }

        ReviewReport report = reviewReportRepository.saveReport(
            request.workspaceId(),
            title,
            status,
            score,
            feedback,
            reportJson
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(report);
    }

    @GetMapping("/reports")
    public ResponseEntity<List<ReviewReport>> getReports(@RequestParam(required = false) UUID workspaceId) {
        access.requireViewer(workspaceId);
        if (workspaceId != null) {
            return ResponseEntity.ok(reviewReportRepository.findReportsByWorkspace(workspaceId));
        }
        return ResponseEntity.ok(reviewReportRepository.findAllReports());
    }

    @GetMapping("/reports/{id}")
    public ResponseEntity<ReviewReport> getReportById(@PathVariable UUID id) {
        var report = reviewReportRepository.findReportById(id);
        if (report.isEmpty()) return ResponseEntity.notFound().build();
        access.requireViewer(report.get().workspaceId());
        return ResponseEntity.ok(report.get());
    }

    @GetMapping("/decisions")
    public ResponseEntity<List<DecisionLogEntry>> getDecisions(@RequestParam(required = false) UUID workspaceId) {
        access.requireViewer(workspaceId);
        if (workspaceId != null) {
            return ResponseEntity.ok(reviewReportRepository.findDecisionsByWorkspace(workspaceId));
        }
        return ResponseEntity.ok(reviewReportRepository.findAllDecisions());
    }
}

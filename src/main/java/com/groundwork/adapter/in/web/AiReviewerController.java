package com.groundwork.adapter.in.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.groundwork.application.DocumentRepository;
import com.groundwork.application.ReviewReportRepository;
import com.groundwork.application.StructuredExtractionService;
import com.groundwork.domain.model.DecisionLogEntry;
import com.groundwork.domain.model.DocumentChunk;
import com.groundwork.domain.model.ReviewReport;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/review")
public class AiReviewerController {

    private final ReviewReportRepository reviewReportRepository;
    private final DocumentRepository documentRepository;
    private final StructuredExtractionService extractionService;
    private final ObjectMapper objectMapper;

    public AiReviewerController(
            ReviewReportRepository reviewReportRepository,
            DocumentRepository documentRepository,
            StructuredExtractionService extractionService,
            ObjectMapper objectMapper) {
        this.reviewReportRepository = reviewReportRepository;
        this.documentRepository = documentRepository;
        this.extractionService = extractionService;
        this.objectMapper = objectMapper;
    }

    public record ReviewRequest(
        UUID workspaceId,
        String documentTitle,
        String content
    ) {}

    @PostMapping
    public ResponseEntity<ReviewReport> reviewContent(@RequestBody ReviewRequest request) {
        String textContent = request.content();
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
            title = "AI Review Report";
        }

        String reportJson = extractionService.performAiReview(title, textContent);

        String status = "APPROVED";
        Double score = 85.0;
        String feedback = "Review completed.";

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
            System.err.println("Error parsing review JSON: " + ex.getMessage());
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
        if (workspaceId != null) {
            return ResponseEntity.ok(reviewReportRepository.findReportsByWorkspace(workspaceId));
        }
        return ResponseEntity.ok(reviewReportRepository.findAllReports());
    }

    @GetMapping("/reports/{id}")
    public ResponseEntity<ReviewReport> getReportById(@PathVariable UUID id) {
        return reviewReportRepository.findReportById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/decisions")
    public ResponseEntity<List<DecisionLogEntry>> getDecisions(@RequestParam(required = false) UUID workspaceId) {
        if (workspaceId != null) {
            return ResponseEntity.ok(reviewReportRepository.findDecisionsByWorkspace(workspaceId));
        }
        return ResponseEntity.ok(reviewReportRepository.findAllDecisions());
    }
}

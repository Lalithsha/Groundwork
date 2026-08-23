package com.groundwork.adapter.in.web;

import com.groundwork.application.DocumentIngestionService;
import com.groundwork.application.DocumentRepository;
import com.groundwork.application.IngestionJobRepository;
import com.groundwork.application.SourceDocumentRepository;
import com.groundwork.application.WorkspaceAccessService;
import com.groundwork.domain.model.IngestionJob;
import com.groundwork.domain.model.SourceDocument;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
public class DocumentUploadController {
    private static final Set<String> TEXT_EXTENSIONS = Set.of("txt", "md", "markdown");
    private static final Set<String> TEXT_MEDIA_TYPES = Set.of(
        MediaType.TEXT_PLAIN_VALUE, "text/markdown", "application/octet-stream");

    private final DocumentRepository chunks;
    private final SourceDocumentRepository documents;
    private final IngestionJobRepository jobs;
    private final DocumentIngestionService ingestion;
    private final WorkspaceAccessService access;
    private final long maxBytes;
    private final int maxPdfPages;

    public DocumentUploadController(DocumentRepository chunks, SourceDocumentRepository documents,
            IngestionJobRepository jobs, DocumentIngestionService ingestion, WorkspaceAccessService access,
            @Value("${groundwork.ingestion.max-file-bytes:20971520}") long maxBytes,
            @Value("${groundwork.ingestion.max-pdf-pages:500}") int maxPdfPages) {
        this.chunks = chunks;
        this.documents = documents;
        this.jobs = jobs;
        this.ingestion = ingestion;
        this.access = access;
        this.maxBytes = maxBytes;
        this.maxPdfPages = maxPdfPages;
    }

    @GetMapping
    public ResponseEntity<List<String>> listDocuments(@RequestParam(required = false) UUID workspaceId) {
        access.requireViewer(workspaceId);
        return ResponseEntity.ok(chunks.findAllTitles(workspaceId));
    }

    @GetMapping("/details")
    public ResponseEntity<List<SourceDocument>> listDocumentDetails(
            @RequestParam(required = false) UUID workspaceId) {
        access.requireViewer(workspaceId);
        return ResponseEntity.ok(documents.findByWorkspace(workspaceId));
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<IngestionJob> getJob(@PathVariable UUID jobId) {
        var job = jobs.findById(jobId);
        if (job.isEmpty()) return ResponseEntity.notFound().build();
        access.requireViewer(job.get().workspaceId());
        return ResponseEntity.ok(job.get());
    }

    @DeleteMapping("/jobs/{jobId}")
    public ResponseEntity<?> cancelJob(@PathVariable UUID jobId) {
        var job = jobs.findById(jobId);
        if (job.isEmpty()) return ResponseEntity.notFound().build();
        access.requireEditor(job.get().workspaceId());
        if (!jobs.cancel(jobId)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Only queued jobs can be cancelled"));
        }
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Map<String, Object>> deleteDocument(@RequestParam String title,
            @RequestParam(required = false) UUID workspaceId) {
        access.requireEditor(workspaceId);
        chunks.deleteByTitle(title, workspaceId);
        ingestion.evictRetrievalCache();
        return ResponseEntity.ok(Map.of("message", "Document deleted successfully", "title", title));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) UUID workspaceId) {
        try {
            access.requireEditor(workspaceId);
            ValidatedUpload upload = validateAndExtract(file);
            DocumentIngestionService.QueuedDocument queued = ingestion.queue(
                workspaceId, upload.filename(), upload.mediaType(), upload.sourceType(), upload.content());

            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("message", queued.duplicate() ? "Document already exists" : "Document accepted for indexing");
            body.put("filename", upload.filename());
            body.put("documentId", queued.document().id());
            body.put("status", queued.document().status());
            body.put("duplicate", queued.duplicate());
            if (queued.job() != null) body.put("jobId", queued.job().id());
            return ResponseEntity.status(queued.duplicate() ? HttpStatus.OK : HttpStatus.ACCEPTED).body(body);
        } catch (InvalidUploadException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        } catch (Exception exception) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Document could not be processed"));
        }
    }

    private ValidatedUpload validateAndExtract(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw new InvalidUploadException("File is empty");
        if (file.getSize() > maxBytes) throw new InvalidUploadException("File exceeds the configured size limit");

        String filename = sanitizeFilename(file.getOriginalFilename());
        String extension = extension(filename);
        String declaredType = file.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : file.getContentType();
        byte[] bytes = file.getBytes();
        boolean pdf = "pdf".equals(extension);

        if (!pdf && !TEXT_EXTENSIONS.contains(extension)) {
            throw new InvalidUploadException("Supported file types are PDF, TXT, and Markdown");
        }
        if (pdf && !looksLikePdf(bytes)) throw new InvalidUploadException("The uploaded file is not a valid PDF");
        if (!pdf && !TEXT_MEDIA_TYPES.contains(declaredType) && !declaredType.startsWith("text/")) {
            throw new InvalidUploadException("The declared media type does not match a supported text document");
        }

        String content = pdf ? extractPdf(bytes) : new String(bytes, StandardCharsets.UTF_8);
        content = cleanText(content);
        if (content.isBlank()) throw new InvalidUploadException("Document contains no readable text");
        return new ValidatedUpload(filename, pdf ? MediaType.APPLICATION_PDF_VALUE : declaredType,
            pdf ? "api_doc" : "readme", content);
    }

    private String extractPdf(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted()) throw new InvalidUploadException("Encrypted PDFs are not supported");
            if (document.getNumberOfPages() > maxPdfPages) {
                throw new InvalidUploadException("PDF exceeds the configured page limit");
            }
            return new PDFTextStripper().getText(document);
        }
    }

    private String cleanText(String text) {
        StringBuilder cleaned = new StringBuilder();
        for (String line : text.replace("\u0000", "").replace("\r\n", "\n").split("\n")) {
            if (line.stripTrailing().matches("(?i)^page \\d+ of \\d+\\s*$")) continue;
            cleaned.append(line.stripTrailing()).append('\n');
        }
        return cleaned.toString().strip();
    }

    private String sanitizeFilename(String original) {
        String fallback = "uploaded-document.txt";
        if (original == null || original.isBlank()) return fallback;
        String normalized = original.replace('\\', '/');
        String leaf = normalized.substring(normalized.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (leaf.isBlank() || leaf.length() > 255) throw new InvalidUploadException("Invalid filename");
        return leaf;
    }

    private String extension(String filename) {
        int separator = filename.lastIndexOf('.');
        return separator < 0 ? "" : filename.substring(separator + 1).toLowerCase(Locale.ROOT);
    }

    private boolean looksLikePdf(byte[] bytes) {
        return bytes.length >= 5 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-';
    }

    private record ValidatedUpload(String filename, String mediaType, String sourceType, String content) {}

    private static final class InvalidUploadException extends RuntimeException {
        private InvalidUploadException(String message) { super(message); }
    }
}

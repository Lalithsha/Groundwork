package com.groundwork.adapter.in.web;

import com.groundwork.application.DocumentRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/documents")
public class DocumentUploadController {

    private final DocumentRepository documentRepository;
    private final StringRedisTemplate redisTemplate;

    public DocumentUploadController(DocumentRepository documentRepository,
                                    StringRedisTemplate redisTemplate) {
        this.documentRepository = documentRepository;
        this.redisTemplate = redisTemplate;
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadDocument(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }

        try {
            String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "uploaded_doc.txt";
            String content;

            if (filename.toLowerCase().endsWith(".pdf")) {
                try (PDDocument pdDocument = Loader.loadPDF(file.getBytes())) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    content = stripper.getText(pdDocument);
                }
            } else {
                content = new String(file.getBytes(), StandardCharsets.UTF_8);
            }

            // Sanitize null bytes (0x00) for PostgreSQL UTF-8 compatibility
            content = content.replace("\u0000", "").trim();

            if (content.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Document contains no readable text content."));
            }

            List<String> chunks = splitTextIntoChunks(content, 500);
            int savedCount = 0;

            for (String chunkText : chunks) {
                if (chunkText.isBlank()) continue;
                String hash = sha256(chunkText);
                String sourceType = filename.toLowerCase().endsWith(".pdf") ? "api_doc" :
                                   (filename.toLowerCase().endsWith(".md") || filename.toLowerCase().endsWith(".txt") ? "readme" : "api_doc");
                documentRepository.save(filename, chunkText, sourceType, hash);
                savedCount++;
            }

            Set<String> keys = redisTemplate.keys("retrieval::*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }

            return ResponseEntity.ok(Map.of(
                "message", "Successfully uploaded and indexed document",
                "filename", filename,
                "chunksIndexed", savedCount
            ));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to process file: " + e.getMessage()));
        }
    }

    private List<String> splitTextIntoChunks(String text, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        String[] paragraphs = text.split("\n\n");
        StringBuilder currentChunk = new StringBuilder();

        for (String p : paragraphs) {
            if (currentChunk.length() + p.length() > chunkSize && currentChunk.length() > 0) {
                chunks.add(currentChunk.toString().trim());
                currentChunk.setLength(0);
            }
            if (currentChunk.length() > 0) {
                currentChunk.append("\n\n");
            }
            currentChunk.append(p);
        }

        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return UUID.randomUUID().toString();
        }
    }
}

package com.groundwork.adapter.in.web;

import com.groundwork.infrastructure.persistence.DocumentEntity;
import com.groundwork.infrastructure.persistence.SpringDataDocumentRepository;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.data.redis.core.RedisTemplate;
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

    private final SpringDataDocumentRepository documentRepository;
    private final EmbeddingModel embeddingModel;
    private final RedisTemplate<String, Object> redisTemplate;

    public DocumentUploadController(SpringDataDocumentRepository documentRepository,
                                    EmbeddingModel embeddingModel,
                                    RedisTemplate<String, Object> redisTemplate) {
        this.documentRepository = documentRepository;
        this.embeddingModel = embeddingModel;
        this.redisTemplate = redisTemplate;
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadDocument(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }

        try {
            String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "uploaded_doc.txt";
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);

            List<String> chunks = splitTextIntoChunks(content, 500);
            int savedCount = 0;

            for (String chunkText : chunks) {
                if (chunkText.isBlank()) continue;
                String hash = sha256(chunkText);

                DocumentEntity entity = new DocumentEntity();
                entity.setTitle(filename);
                entity.setContent(chunkText);
                entity.setSourceType("custom_upload");
                entity.setContentHash(hash);

                try {
                    float[] embed = embeddingModel.embed(chunkText);
                    entity.setEmbedding(embed);
                } catch (Exception e) {
                    entity.setEmbedding(new float[1536]);
                }

                documentRepository.save(entity);
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

package com.groundwork.application;

import com.groundwork.application.port.out.EmbeddingPort;
import com.groundwork.domain.model.IngestionJob;
import com.groundwork.domain.model.SourceDocument;
import com.groundwork.domain.model.TextChunk;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentIngestionService {
    private static final int EMBEDDING_BATCH_SIZE = 50;

    private final SourceDocumentRepository sourceDocuments;
    private final IngestionJobRepository jobs;
    private final DocumentRepository chunks;
    private final TextChunker chunker;
    private final EmbeddingPort embeddings;
    private final CacheManager cacheManager;

    public DocumentIngestionService(SourceDocumentRepository sourceDocuments, IngestionJobRepository jobs,
            DocumentRepository chunks, TextChunker chunker, EmbeddingPort embeddings, CacheManager cacheManager) {
        this.sourceDocuments = sourceDocuments;
        this.jobs = jobs;
        this.chunks = chunks;
        this.chunker = chunker;
        this.embeddings = embeddings;
        this.cacheManager = cacheManager;
    }

    public QueuedDocument queue(UUID workspaceId, String title, String mediaType, String sourceType, String content) {
        String hash = Hashing.sha256(content);
        var existing = sourceDocuments.findByContentHash(workspaceId, hash);
        if (existing.isPresent()) {
            SourceDocument document = existing.get();
            IngestionJob active = jobs.findActiveByDocumentId(document.id()).orElse(null);
            if (active == null && "FAILED".equals(document.status())) {
                sourceDocuments.markQueued(document.id());
                IngestionJob retry = jobs.create(document.id(), workspaceId);
                SourceDocument queuedDocument = sourceDocuments.findByContentHash(workspaceId, hash).orElse(document);
                return new QueuedDocument(queuedDocument, retry, false);
            }
            return new QueuedDocument(document, active, true);
        }
        SourceDocument document = sourceDocuments.create(workspaceId, title, mediaType, sourceType, content, hash);
        return new QueuedDocument(document, jobs.create(document.id(), workspaceId), false);
    }

    public void process(IngestionJob job) {
        SourceDocumentRepository.SourceContent source = sourceDocuments.findContentById(job.documentId())
            .orElseThrow(() -> new IllegalStateException("Source document no longer exists"));
        int total = index(source, (current, count) -> jobs.updateProgress(job.id(), current, count));
        jobs.complete(job.id(), total);
    }

    public void reindexDocument(UUID documentId) {
        SourceDocumentRepository.SourceContent source = sourceDocuments.findContentById(documentId)
            .orElseThrow(() -> new IllegalStateException("Source document no longer exists"));
        index(source, (current, total) -> {});
    }

    private int index(SourceDocumentRepository.SourceContent source, ProgressListener progress) {
        sourceDocuments.markProcessing(source.id());
        List<TextChunk> textChunks = chunker.chunk(source.rawContent());
        if (textChunks.isEmpty()) throw new IllegalArgumentException("Document contains no indexable text");
        progress.update(0, textChunks.size());

        List<double[]> vectors = new ArrayList<>(textChunks.size());
        for (int start = 0; start < textChunks.size(); start += EMBEDDING_BATCH_SIZE) {
            int end = Math.min(start + EMBEDDING_BATCH_SIZE, textChunks.size());
            vectors.addAll(embeddings.embed(textChunks.subList(start, end).stream().map(TextChunk::content).toList()));
            progress.update(end, textChunks.size());
        }
        chunks.replaceDocumentChunks(source.id(), source.workspaceId(), source.title(), source.sourceType(),
            textChunks, vectors, embeddings.modelName(), embeddings.modelVersion());
        sourceDocuments.markReady(source.id(), embeddings.modelName());
        evictRetrievalCache();
        return textChunks.size();
    }

    public void evictRetrievalCache() {
        Cache retrieval = cacheManager.getCache("retrieval");
        if (retrieval != null) retrieval.clear();
    }

    public record QueuedDocument(SourceDocument document, IngestionJob job, boolean duplicate) {}
    @FunctionalInterface
    private interface ProgressListener { void update(int current, int total); }
}

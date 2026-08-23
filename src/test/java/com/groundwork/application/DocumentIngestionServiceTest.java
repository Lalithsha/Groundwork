package com.groundwork.application;

import com.groundwork.adapter.out.ai.DeterministicEmbeddingAdapter;
import com.groundwork.domain.model.IngestionJob;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentIngestionServiceTest {
    @Test
    void indexesAndCompletesDurableJob() {
        SourceDocumentRepository sources = mock(SourceDocumentRepository.class);
        IngestionJobRepository jobs = mock(IngestionJobRepository.class);
        DocumentRepository chunks = mock(DocumentRepository.class);
        CacheManager caches = mock(CacheManager.class);
        UUID documentId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        IngestionJob job = new IngestionJob(jobId, documentId, null, "RUNNING", 0, 0, 1, 3, null,
            Instant.now(), Instant.now(), null);
        when(sources.findContentById(documentId)).thenReturn(Optional.of(new SourceDocumentRepository.SourceContent(
            documentId, null, "spec.md", "text/markdown", "readme", "# Spec\nThe service must retry delivery.", "hash", "QUEUED")));

        DocumentIngestionService service = new DocumentIngestionService(sources, jobs, chunks,
            new TokenAwareTextChunker(100, 10), new DeterministicEmbeddingAdapter(16), caches);
        service.process(job);

        verify(sources).markProcessing(documentId);
        verify(chunks).replaceDocumentChunks(any(), any(), any(), any(), any(), any(), any(), any());
        verify(sources).markReady(documentId, "groundwork-local-hashing");
        verify(jobs).complete(jobId, 1);
        verify(jobs).updateProgress(jobId, 1, 1);
    }
}

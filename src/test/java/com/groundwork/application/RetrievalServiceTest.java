package com.groundwork.application;

import com.groundwork.adapter.out.ai.CohereRerankAdapter;
import com.groundwork.adapter.out.ai.DeterministicEmbeddingAdapter;
import com.groundwork.domain.model.DocumentChunk;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RetrievalServiceTest {
    @Test
    void reciprocalRankFusionRewardsChunksPresentInBothLists() {
        RetrievalService service = new RetrievalService(mock(DocumentRepository.class), mock(CohereRerankAdapter.class),
            new DeterministicEmbeddingAdapter(16), new SimpleMeterRegistry());
        DocumentChunk shared = chunk("shared");
        DocumentChunk vectorOnly = chunk("vector");
        DocumentChunk keywordOnly = chunk("keyword");

        List<DocumentChunk> result = service.fuse(List.of(shared, vectorOnly), List.of(shared, keywordOnly));

        assertThat(result.getFirst().id()).isEqualTo(shared.id());
        assertThat(result).extracting(DocumentChunk::id)
            .containsExactlyInAnyOrder(shared.id(), vectorOnly.id(), keywordOnly.id());
    }

    private DocumentChunk chunk(String title) {
        return new DocumentChunk(UUID.randomUUID(), UUID.randomUUID(), title, title + " content", "readme",
            title, 0.5, 0, null, null);
    }
}

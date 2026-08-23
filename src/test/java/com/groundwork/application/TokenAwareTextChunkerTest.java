package com.groundwork.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenAwareTextChunkerTest {
    @Test
    void producesOverlappingBoundedChunks() {
        TokenAwareTextChunker chunker = new TokenAwareTextChunker(100, 10);
        String content = "# Architecture\n" + String.join(" ", java.util.Collections.nCopies(230, "token"));

        var chunks = chunker.chunk(content);

        assertThat(chunks).hasSize(3);
        assertThat(chunks.getFirst().tokenCount()).isEqualTo(100);
        assertThat(chunks.get(1).tokenCount()).isEqualTo(100);
        assertThat(chunks.getFirst().sectionTitle()).isEqualTo("Architecture");
        assertThat(chunks.get(2).index()).isEqualTo(2);
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new TokenAwareTextChunker(100, 100))
            .isInstanceOf(IllegalArgumentException.class);
    }
}

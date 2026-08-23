package com.groundwork.adapter.out.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicEmbeddingAdapterTest {
    @Test
    void isStableAndNormalized() {
        DeterministicEmbeddingAdapter adapter = new DeterministicEmbeddingAdapter(128);
        double[] first = adapter.embed("PostgreSQL vector retrieval");
        double[] second = adapter.embed("PostgreSQL vector retrieval");

        assertThat(first).containsExactly(second);
        double magnitude = Math.sqrt(java.util.Arrays.stream(first).map(value -> value * value).sum());
        assertThat(magnitude).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.000001));
    }

    @Test
    void sharedTermsProducePositiveSimilarity() {
        DeterministicEmbeddingAdapter adapter = new DeterministicEmbeddingAdapter(256);
        double[] first = adapter.embed("webhook retry delivery");
        double[] second = adapter.embed("delivery retry policy");
        double dot = 0;
        for (int i = 0; i < first.length; i++) dot += first[i] * second[i];
        assertThat(dot).isPositive();
    }
}

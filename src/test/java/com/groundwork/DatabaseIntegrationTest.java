package com.groundwork;

import com.groundwork.application.DocumentIngestionService;
import com.groundwork.application.IngestionJobRepository;
import com.groundwork.application.RetrievalService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_DATABASE_INTEGRATION_TESTS", matches = "true")
class DatabaseIntegrationTest {
    @Autowired DocumentIngestionService ingestion;
    @Autowired IngestionJobRepository jobs;
    @Autowired RetrievalService retrieval;

    @Test
    void ingestsEmbedsAndRetrievesDocumentEndToEnd() {
        var queued = ingestion.queue(null, "retry-policy.md", "text/markdown", "readme",
            "# Retry Policy\nWebhook delivery retries five times before entering the dead letter queue.");
        var job = jobs.findById(queued.job().id()).orElseThrow();

        ingestion.process(job);

        var results = retrieval.retrieve("What happens after webhook retries?", "hybrid", 4);
        assertThat(results).isNotEmpty();
        assertThat(results.getFirst().title()).isEqualTo("retry-policy.md");
        assertThat(results.getFirst().documentId()).isEqualTo(queued.document().id());
    }
}

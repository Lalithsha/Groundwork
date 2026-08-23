package com.groundwork.evidence.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiDiffAnalyzerTest {
    @Test
    void reportsRemovedOperationsAndAddedPathsDeterministically() {
        String base = """
            openapi: 3.0.3
            paths:
              /customers/{id}:
                get: {responses: {'200': {description: ok}}}
                delete: {responses: {'204': {description: gone}}}
            """;
        String head = """
            openapi: 3.0.3
            paths:
              /customers/{id}:
                get: {responses: {'200': {description: ok}}}
              /customers:
                post: {responses: {'201': {description: created}}}
            """;

        var result = new OpenApiDiffAnalyzer().compare(base, head);
        assertThat(result.breaking()).isTrue();
        assertThat(result.breakingChanges()).containsExactly("Removed operation DELETE /customers/{id}");
        assertThat(result.additions()).containsExactly("Added path /customers");
    }
}

package com.groundwork.evidence.application;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CodeOwnersMatcherTest {
    @Test
    void appliesLastMatchingRuleAndSupportsRootAndRecursivePatterns() {
        String rules = """
            src/** @platform
            src/payments/** @payments @security
            /openapi.yaml @api-team
            """;
        var matches = new CodeOwnersMatcher().ownersFor(
            List.of("src/catalog/Item.java", "src/payments/Card.java", "openapi.yaml"), rules);

        assertThat(matches.get("src/catalog/Item.java")).containsExactly("@platform");
        assertThat(matches.get("src/payments/Card.java")).containsExactly("@payments", "@security");
        assertThat(matches.get("openapi.yaml")).containsExactly("@api-team");
    }
}

package com.groundwork.evidence.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConnectorCredentialCipherTest {
    private final ConnectorCredentialCipher cipher = new ConnectorCredentialCipher(
        "unit-test-connector-key-that-is-intentionally-long-and-unique");

    @Test
    void encryptsWithRandomNonceAndBindsCiphertextToConnectionContext() {
        String first = cipher.encrypt("refresh-token", "workspace:provider:account");
        String second = cipher.encrypt("refresh-token", "workspace:provider:account");

        assertThat(first).startsWith("v1:").isNotEqualTo(second);
        assertThat(cipher.decrypt(first, "workspace:provider:account")).isEqualTo("refresh-token");
        assertThatThrownBy(() -> cipher.decrypt(first, "different-context"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void decryptsPreviousVersionDuringKeyRotationAndEncryptsOnlyWithCurrentVersion() {
        String oldSecret = "old-unit-test-connector-key-that-is-long-and-intentionally-unique";
        String currentSecret = "new-unit-test-connector-key-that-is-long-and-intentionally-unique";
        String oldCiphertext = new ConnectorCredentialCipher(oldSecret, 1, "", 0)
            .encrypt("refresh-token", "workspace:provider:account");
        ConnectorCredentialCipher rotating = new ConnectorCredentialCipher(currentSecret, 2, oldSecret, 1);

        assertThat(rotating.decrypt(oldCiphertext, "workspace:provider:account")).isEqualTo("refresh-token");
        assertThat(rotating.encrypt("new-token", "workspace:provider:account")).startsWith("v2:");
        assertThat(rotating.currentVersion()).isEqualTo(2);
    }
}

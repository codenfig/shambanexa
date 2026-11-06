package com.shambanexa.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionChecksTest {

    @Test
    void prodRejectsThePublishedSecretAndTheDevCorsDefault() {
        assertThrows(IllegalStateException.class, () -> ProductionChecks.requireDeploySecrets(
                "shambanexa-dev-secret-change-before-any-shared-deployment-32",
                "https://app.example.com"));
        assertThrows(IllegalStateException.class, () -> ProductionChecks.requireDeploySecrets(
                "a-private-secret-that-is-long-enough-for-hmac",
                "http://localhost:60575"));
    }

    @Test
    void prodAcceptsAPrivateSecretAndAnExplicitOrigin() {
        assertDoesNotThrow(() -> ProductionChecks.requireDeploySecrets(
                "a-private-secret-that-is-long-enough-for-hmac",
                "https://app.example.com"));
    }
}

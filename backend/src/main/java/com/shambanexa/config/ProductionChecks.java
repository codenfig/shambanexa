package com.shambanexa.config;

public final class ProductionChecks {

    static final String DEV_SECRET_PREFIX = "shambanexa-dev-secret";
    static final String DEV_CORS = "http://localhost:60575";

    private ProductionChecks() {
    }

    public static void requireDeploySecrets(String secret, String origins) {
        if (secret == null || secret.length() < 32 || secret.startsWith(DEV_SECRET_PREFIX)) {
            throw new IllegalStateException("Set JWT_SECRET to a private value of at least 32 characters before starting the prod profile.");
        }
        if (origins == null || origins.isBlank() || origins.equals(DEV_CORS)) {
            throw new IllegalStateException("Set CORS_ORIGINS to the public site before starting the prod profile.");
        }
    }
}

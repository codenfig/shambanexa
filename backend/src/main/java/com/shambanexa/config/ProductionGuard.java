package com.shambanexa.config;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProductionGuard {

    public ProductionGuard(AppProperties properties) {
        ProductionChecks.requireDeploySecrets(properties.getJwt().getSecret(), properties.getCors().getOrigins());
    }
}

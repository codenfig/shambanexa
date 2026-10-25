package com.shambanexa.dto;

import java.time.LocalDate;

public record SettingsResponse(
        String tenantName,
        String code,
        String currencyCode,
        String timezone,
        String seasonLabel,
        String locale,
        String subscriptionPlan,
        LocalDate subscriptionValidUntil
) {
}

package com.shambanexa.dto;

import java.time.Instant;
import java.util.UUID;

public record AlertResponse(
UUID id,
        String severity,
        String title,
        String body,
        String status,
        UUID farmId,
        String farmName,
        Instant occurredAt,
        String sourceType
) {
}

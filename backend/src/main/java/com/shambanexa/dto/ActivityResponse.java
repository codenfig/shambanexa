package com.shambanexa.dto;

import java.time.Instant;
import java.util.UUID;

public record ActivityResponse(
        UUID id,
        String eventType,
        String title,
        String detail,
        UUID farmId,
        Instant occurredAt,
        String farmName,
        String farmCode
) {
}

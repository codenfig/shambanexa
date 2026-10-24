package com.shambanexa.dto;

import java.time.Instant;
import java.util.UUID;

public record AdviceResponse(
        UUID id,
        String topic,
        String advice,
        String medication,
        String medicationNote,
        UUID authorId,
        String authorName,
        Instant createdAt,
        boolean mine
) {
}

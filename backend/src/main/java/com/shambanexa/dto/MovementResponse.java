package com.shambanexa.dto;

import java.time.Instant;
import java.util.UUID;

public record MovementResponse(
        UUID id,
        String eventType,
        String subjectType,
        UUID subjectId,
        String subjectCode,
        String subjectName,
        String speciesName,
        int quantity,
        UUID farmId,
        String farmName,
        UUID sourceFarmId,
        String sourceFarmName,
        UUID destinationFarmId,
        String destinationFarmName,
        Instant effectiveAt,
        String reason,
        String actorName
) {
}

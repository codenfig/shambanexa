package com.shambanexa.dto;

import java.time.LocalDate;
import java.util.UUID;

public record HealthCaseResponse(
UUID id,
        String title,
        String caseStatus,
        String severity,
        LocalDate openedOn,
        String notes,
        UUID farmId,
        String farmName
) {
}

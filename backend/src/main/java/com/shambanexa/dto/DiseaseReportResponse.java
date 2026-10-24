package com.shambanexa.dto;

import java.time.LocalDate;
import java.util.UUID;

public record DiseaseReportResponse(
        UUID id,
        UUID alertId,
        UUID farmId,
        String farmName,
        String diseaseName,
        int animalsAffected,
        String severity,
        LocalDate noticedOn,
        String signs
) {
}

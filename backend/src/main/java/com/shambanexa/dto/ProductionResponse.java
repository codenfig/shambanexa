package com.shambanexa.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ProductionResponse(
UUID id,
        UUID farmId,
        String farmName,
        String productType,
        BigDecimal quantity,
        String unit,
        LocalDate recordedOn,
        String notes
) {
}

package com.shambanexa.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ProductionRequest(
@jakarta.validation.constraints.NotNull UUID farmId,
        @jakarta.validation.constraints.NotBlank String productType,
        @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive BigDecimal quantity,
        @jakarta.validation.constraints.NotBlank String unit,
        LocalDate recordedOn,
        @jakarta.validation.constraints.Size(max = 300) String notes
) {
}

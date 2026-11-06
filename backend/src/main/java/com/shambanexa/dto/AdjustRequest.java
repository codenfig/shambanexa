package com.shambanexa.dto;

public record AdjustRequest(
        @jakarta.validation.constraints.NotNull java.math.BigDecimal delta,
        @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 300) String reason,
        java.util.UUID batchId,
        @jakarta.validation.constraints.Size(max = 40) String reasonCode,
        @jakarta.validation.constraints.Size(max = 400) String notes
) {
}

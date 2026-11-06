package com.shambanexa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record StockTransferRequest(
        @NotNull UUID destinationFarmId,
        @NotNull @Positive BigDecimal quantity,
        @NotBlank @Size(max = 300) String reason,
        @Size(max = 160) String destinationStore,
        UUID batchId,
        @Size(max = 300) String overrideReason
) {
}

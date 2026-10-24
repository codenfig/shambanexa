package com.shambanexa.dto;

import java.util.UUID;

public record TransferRequest(
@jakarta.validation.constraints.NotNull UUID destinationFarmId,
        @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 300) String reason,
        @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 70) String idempotencyKey
) {
}

package com.shambanexa.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InventoryRequest(
@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 40) String sku,
        @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 160) String name,
        @jakarta.validation.constraints.NotBlank String category,
        @jakarta.validation.constraints.NotNull UUID farmId,
        @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.PositiveOrZero BigDecimal quantityOnHand,
        @jakarta.validation.constraints.NotBlank String unit,
        @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.PositiveOrZero BigDecimal reorderPoint,
        String lotCode,
        LocalDate expiresOn
) {
}

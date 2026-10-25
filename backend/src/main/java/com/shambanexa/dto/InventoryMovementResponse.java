package com.shambanexa.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InventoryMovementResponse(
        UUID id,
        UUID itemId,
        String itemName,
        String sku,
        UUID farmId,
        String farmName,
        String movementType,
        BigDecimal quantityDelta,
        BigDecimal quantityAfter,
        String unit,
        String reason,
        String lotCode,
        LocalDate expiresOn,
        String supplierName,
        String storeName,
        String actorName,
        Instant occurredAt
) {
}

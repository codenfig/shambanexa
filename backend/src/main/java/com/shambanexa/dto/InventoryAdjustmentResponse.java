package com.shambanexa.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InventoryAdjustmentResponse(
        UUID id,
        UUID itemId,
        String itemName,
        String sku,
        UUID farmId,
        String farmName,
        BigDecimal delta,
        String unit,
        String reason,
        String status,
        String requestedByName,
        Instant requestedAt,
        String decidedByName,
        Instant decidedAt
) {
}

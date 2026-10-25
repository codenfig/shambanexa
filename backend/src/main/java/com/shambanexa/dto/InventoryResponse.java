package com.shambanexa.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InventoryResponse(
UUID id,
        String sku,
        String name,
        String category,
        UUID farmId,
        String farmName,
        BigDecimal quantityOnHand,
        BigDecimal reservedQuantity,
        BigDecimal available,
        String unit,
        BigDecimal reorderPoint,
        boolean lowStock,
        String lotCode,
        LocalDate expiresOn,
        BigDecimal unitCost,
        BigDecimal stockValue,
        String supplierName,
        String storeName,
        long version
) {
}

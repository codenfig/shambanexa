package com.shambanexa.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record StockBatchView(
        UUID id,
        UUID itemId,
        String productName,
        String sku,
        String category,
        String productType,
        UUID farmId,
        String farmName,
        String lotCode,
        BigDecimal quantityReceived,
        BigDecimal quantityRemaining,
        LocalDate manufacturedOn,
        LocalDate receivedOn,
        LocalDate expiresOn,
        LocalDate bestBeforeOn,
        LocalDate openedOn,
        Integer openDays,
        LocalDate useBy,
        Integer daysLeft,
        String status,
        String storeName,
        String supplierName,
        BigDecimal unitCost,
        String unit
) {
}

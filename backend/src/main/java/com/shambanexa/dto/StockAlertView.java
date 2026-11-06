package com.shambanexa.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record StockAlertView(
        UUID batchId,
        UUID itemId,
        String kind,
        String severity,
        String productName,
        String category,
        String lotCode,
        BigDecimal quantity,
        LocalDate useBy,
        Integer daysLeft,
        String action,
        String assigneeName,
        boolean acknowledged,
        String farmName
) {
}

package com.shambanexa.dto;

import java.math.BigDecimal;
import java.util.List;

public record InventoryReportView(
        BigDecimal totalValue,
        String currency,
        List<CategoryValue> byCategory,
        List<WindowCount> expiring,
        BigDecimal expiredValue,
        long expiredBatches,
        long nearExpiry,
        long quarantined
) {
    public record CategoryValue(String category, int products, BigDecimal value) {
    }

    public record WindowCount(int days, long batches) {
    }
}

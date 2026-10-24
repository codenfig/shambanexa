package com.shambanexa.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record DashboardResponse(
Instant generatedAt,
        String seasonLabel,
        String currencyCode,
        long totalAnimals,
        BigDecimal animalChangePercent,
        long farmCount,
        long newFarmsThisQuarter,
        BigDecimal milkTodayLitres,
        BigDecimal milkDeltaVsYesterday,
        BigDecimal revenue,
        BigDecimal revenueChangePercent,
        int fieldCount,
        int regionCount,
        boolean reconciled,
        List<SpeciesCount> species,
        List<FarmPin> locations,
        List<TrendPoint> trends,
        List<AlertResponse> alerts,
        List<ActivityResponse> activity,
        SubscriptionView subscription,
        int openAlertCount
) {
}

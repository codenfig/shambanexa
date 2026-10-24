package com.shambanexa.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record FarmForecastResponse(
        UUID farmId,
        String farmName,
        boolean available,
        String message,
        String source,
        String attribution,
        Instant retrievedAt,
        boolean cached,
        BigDecimal currentTemperatureC,
        BigDecimal currentHumidity,
        BigDecimal currentRainMm,
        BigDecimal currentWindMs,
        List<Day> days,
        List<ReportedIllness> reported,
        List<String> checks
) {
    public FarmForecastResponse withCached(boolean cached) {
        return new FarmForecastResponse(
                farmId, farmName, available, message, source, attribution, retrievedAt, cached,
                currentTemperatureC, currentHumidity, currentRainMm, currentWindMs, days, reported, checks
        );
    }

    public record Day(
            LocalDate date,
            String label,
            BigDecimal highC,
            BigDecimal lowC,
            BigDecimal rainMm,
            Integer rainChance,
            BigDecimal humidity,
            BigDecimal windMs
    ) {
    }

    public record ReportedIllness(
            UUID id,
            String title,
            String sourceType,
            String farmName,
            String notes,
            LocalDate openedOn
    ) {
    }
}

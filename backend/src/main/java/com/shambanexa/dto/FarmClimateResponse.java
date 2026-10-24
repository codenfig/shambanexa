package com.shambanexa.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FarmClimateResponse(
        UUID farmId,
        String farmName,
        boolean available,
        String message,
        String source,
        String attribution,
        Instant retrievedAt,
        boolean cached,
        LocalDate start,
        LocalDate end,
        int days,
        BigDecimal rainfallMm,
        BigDecimal rainfallNormalMm,
        String rainfallBalance,
        BigDecimal evapotranspirationMm,
        BigDecimal evapotranspirationNormalMm,
        String evapotranspirationBalance,
        BigDecimal averageSurfaceWetness,
        BigDecimal averageRootWetness,
        BigDecimal rootWetnessNormal,
        String rootWetnessBalance,
        BigDecimal averageMaxC,
        BigDecimal averageMinC,
        BigDecimal averageHumidity,
        BigDecimal averageDewPointC,
        BigDecimal averageWetBulbC,
        Integer hotDays,
        Integer coldNights,
        Integer heatStressDays,
        BigDecimal growingDegreeDays,
        BigDecimal averageSunlightMj,
        Integer sunlightDays,
        BigDecimal averageParMj,
        BigDecimal parNormalMj,
        BigDecimal averageWindMs,
        Integer dryDays,
        Integer longestDrySpell
) {
    public FarmClimateResponse withCached(boolean cached) {
        return new FarmClimateResponse(
                farmId, farmName, available, message, source, attribution, retrievedAt, cached,
                start, end, days, rainfallMm, rainfallNormalMm, rainfallBalance,
                evapotranspirationMm, evapotranspirationNormalMm, evapotranspirationBalance,
                averageSurfaceWetness, averageRootWetness, rootWetnessNormal, rootWetnessBalance,
                averageMaxC, averageMinC, averageHumidity, averageDewPointC, averageWetBulbC,
                hotDays, coldNights, heatStressDays, growingDegreeDays,
                averageSunlightMj, sunlightDays, averageParMj, parNormalMj, averageWindMs,
                dryDays, longestDrySpell
        );
    }
}

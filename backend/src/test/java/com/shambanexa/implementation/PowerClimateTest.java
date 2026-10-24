package com.shambanexa.implementation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PowerClimateTest {

    @Test
    void recentEvaporationScaledByTwentyFourReturnsToMillimetres() {
        assertEquals(3.27, PowerClimate.evaporationMm(3.27), 0.0001);
        assertEquals(3.27, PowerClimate.evaporationMm(78.48), 0.0001);
    }

    @Test
    void countsDrySpellsHeatAndGrowingDegreesFromDailyValues() {
        LocalDate start = LocalDate.of(2026, 9, 22);
        Map<LocalDate, Double> rain = Map.of(
                start, 0.0,
                start.plusDays(1), 0.0,
                start.plusDays(2), 5.0,
                start.plusDays(3), 0.0,
                start.plusDays(4), 0.0,
                start.plusDays(5), 0.0
        );
        Map<LocalDate, Double> maximum = Map.of(start, 36.0, start.plusDays(1), 28.0);
        Map<LocalDate, Double> minimum = Map.of(start, 24.0, start.plusDays(1), 16.0);
        Map<LocalDate, Double> humidity = Map.of(start, 70.0, start.plusDays(1), 40.0);
        PowerClimate.Assessment assessment = PowerClimate.assess(
                start, start.plusDays(5),
                rain, maximum, minimum, humidity,
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of("SEP", 1.0), Map.of(), Map.of(), Map.of(), Map.of()
        );
        assertEquals(5, assessment.dryDays());
        assertEquals(3, assessment.longestDrySpell());
        assertEquals(1, assessment.hotDays());
        assertEquals(0, assessment.coldNights());
        assertEquals(1, assessment.heatStressDays());
        assertEquals(new BigDecimal("32.0"), assessment.growingDegreeDays());
        assertEquals(new BigDecimal("6.0"), assessment.rainfallNormalMm());
        assertEquals("below", assessment.rainfallBalance());
    }

    @Test
    void missingSeriesStayEmpty() {
        LocalDate day = LocalDate.of(2026, 10, 1);
        PowerClimate.Assessment assessment = PowerClimate.assess(
                day, day,
                Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                null, null, null, null, null
        );
        assertEquals(0, assessment.days());
        assertNull(assessment.dryDays());
        assertNull(assessment.rainfallMm());
        assertNull(assessment.rainfallBalance());
    }
}

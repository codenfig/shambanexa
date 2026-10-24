package com.shambanexa.implementation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

final class PowerClimate {

    static final double DRY_DAY_MM = 1.0;
    static final double HOT_DAY_C = 32.0;
    static final double COLD_NIGHT_C = 10.0;
    static final double GROWING_DEGREE_BASE_C = 10.0;
    static final double HEAT_STRESS_INDEX = 72.0;

    private PowerClimate() {
    }

    /**
     * Recent POWER daily evaporation is sometimes exactly 24 times the millimetre value.
     * Values above 15 mm/day are scaled back so the card stays in millimetres.
     */
    static double evaporationMm(double raw) {
        return raw > 15.0 ? raw / 24.0 : raw;
    }

    static Assessment assess(
            LocalDate start,
            LocalDate end,
            Map<LocalDate, Double> rain,
            Map<LocalDate, Double> maximum,
            Map<LocalDate, Double> minimum,
            Map<LocalDate, Double> humidity,
            Map<LocalDate, Double> dewPoint,
            Map<LocalDate, Double> wetBulb,
            Map<LocalDate, Double> wind,
            Map<LocalDate, Double> sunlight,
            Map<LocalDate, Double> plantLight,
            Map<LocalDate, Double> surfaceWetness,
            Map<LocalDate, Double> rootWetness,
            Map<LocalDate, Double> evaporation,
            Map<String, Double> rainNormal,
            Map<String, Double> evaporationNormal,
            Map<String, Double> surfaceNormal,
            Map<String, Double> rootNormal,
            Map<String, Double> plantLightNormal
    ) {
        int days = Math.max(rain.size(), Math.max(maximum.size(), Math.max(minimum.size(), humidity.size())));
        Double rainfall = sum(rain);
        Double rainfallExpected = coveredSum(start, end, rainNormal);
        Double evaporationSum = sum(evaporation);
        Double evaporationExpected = coveredSum(start, end, evaporationNormal);
        Double surface = percent(average(surfaceWetness));
        Double root = percent(average(rootWetness));
        Double rootExpected = percent(coveredAverage(start, end, rootNormal));
        Heat heat = heat(maximum, minimum, humidity);
        return new Assessment(
                days,
                round(rainfall),
                round(rainfallExpected),
                balance(rainfall, rainfallExpected),
                round(evaporationSum),
                round(evaporationExpected),
                balance(evaporationSum, evaporationExpected),
                round(surface),
                round(root),
                round(rootExpected),
                balance(root, rootExpected),
                round(average(maximum)),
                round(average(minimum)),
                round(average(humidity)),
                round(average(dewPoint)),
                round(average(wetBulb)),
                maximum.isEmpty() ? null : heat.hotDays,
                minimum.isEmpty() ? null : heat.coldNights,
                humidity.isEmpty() ? null : heat.heatStressDays,
                heat.growingDegreeDays == null ? null : round(heat.growingDegreeDays),
                round(average(sunlight)),
                sunlight.isEmpty() ? null : sunlight.size(),
                round(average(plantLight)),
                round(coveredAverage(start, end, plantLightNormal)),
                round(average(wind)),
                rain.isEmpty() ? null : dryDays(rain),
                rain.isEmpty() ? null : longestDrySpell(rain)
        );
    }

    static String balance(Double actual, Double normal) {
        if (actual == null || normal == null || normal == 0.0) {
            return null;
        }
        double ratio = actual / normal;
        if (ratio > 1.15) {
            return "above";
        }
        if (ratio < 0.85) {
            return "below";
        }
        return "near";
    }

    private static Heat heat(
            Map<LocalDate, Double> maximum,
            Map<LocalDate, Double> minimum,
            Map<LocalDate, Double> humidity
    ) {
        int hot = 0;
        int cold = 0;
        int stress = 0;
        boolean paired = false;
        double growing = 0;
        Map<LocalDate, Boolean> days = new TreeMap<>();
        maximum.keySet().forEach(day -> days.put(day, true));
        minimum.keySet().forEach(day -> days.put(day, true));
        for (LocalDate day : days.keySet()) {
            Double high = maximum.get(day);
            Double low = minimum.get(day);
            if (high != null && high >= HOT_DAY_C) {
                hot++;
            }
            if (low != null && low <= COLD_NIGHT_C) {
                cold++;
            }
            if (high == null || low == null) {
                continue;
            }
            paired = true;
            double mean = (high + low) / 2.0;
            growing += Math.max(0, mean - GROWING_DEGREE_BASE_C);
            Double rh = humidity.get(day);
            if (rh != null && livestockIndex(mean, rh) >= HEAT_STRESS_INDEX) {
                stress++;
            }
        }
        return new Heat(hot, cold, stress, paired ? growing : null);
    }

    static double livestockIndex(double meanC, double relativeHumidity) {
        return (1.8 * meanC + 32) - ((0.55 - 0.0055 * relativeHumidity) * (1.8 * meanC - 26));
    }

    private static int dryDays(Map<LocalDate, Double> rain) {
        int count = 0;
        for (Double value : rain.values()) {
            if (value < DRY_DAY_MM) {
                count++;
            }
        }
        return count;
    }

    private static int longestDrySpell(Map<LocalDate, Double> rain) {
        int longest = 0;
        int run = 0;
        LocalDate previous = null;
        for (Map.Entry<LocalDate, Double> entry : new TreeMap<>(rain).entrySet()) {
            boolean dry = entry.getValue() < DRY_DAY_MM;
            boolean consecutive = previous != null && previous.plusDays(1).equals(entry.getKey());
            if (!dry) {
                run = 0;
            } else if (consecutive) {
                run++;
            } else {
                run = 1;
            }
            longest = Math.max(longest, run);
            previous = entry.getKey();
        }
        return longest;
    }

    private static Double sum(Map<LocalDate, Double> series) {
        if (series.isEmpty()) {
            return null;
        }
        double total = 0;
        for (Double value : series.values()) {
            total += value;
        }
        return total;
    }

    private static Double average(Map<LocalDate, Double> series) {
        Double total = sum(series);
        return total == null ? null : total / series.size();
    }

    private static Double percent(Double fraction) {
        return fraction == null ? null : fraction * 100.0;
    }

    private static Double coveredSum(LocalDate start, LocalDate end, Map<String, Double> monthly) {
        if (monthly == null || monthly.isEmpty()) {
            return null;
        }
        double total = 0;
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            Double value = monthly.get(month(day));
            if (value == null) {
                return null;
            }
            total += value;
        }
        return total;
    }

    private static Double coveredAverage(LocalDate start, LocalDate end, Map<String, Double> monthly) {
        Double total = coveredSum(start, end, monthly);
        if (total == null) {
            return null;
        }
        long days = end.toEpochDay() - start.toEpochDay() + 1;
        return total / days;
    }

    private static String month(LocalDate day) {
        return day.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase(Locale.ENGLISH);
    }

    private static BigDecimal round(Double value) {
        if (value == null) {
            return null;
        }
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
    }

    private record Heat(int hotDays, int coldNights, int heatStressDays, Double growingDegreeDays) {
    }

    record Assessment(
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
    }
}

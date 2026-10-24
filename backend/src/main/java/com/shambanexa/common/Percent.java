package com.shambanexa.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Percent {

    private Percent() {
    }

    public static BigDecimal change(long current, long previous) {
        if (previous == 0) {
            return null;
        }
        return BigDecimal.valueOf(current - previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(previous), 1, RoundingMode.HALF_UP);
    }

    public static BigDecimal change(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.signum() == 0 || current == null) {
            return null;
        }
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 1, RoundingMode.HALF_UP);
    }
}

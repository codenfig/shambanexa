package com.shambanexa.implementation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

public final class InventoryStock {

    public static final Set<String> CATEGORIES = Set.of("FEED", "MEDICINE", "EQUIPMENT", "SUPPLY");

    private InventoryStock() {
    }

    public static String category(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (!CATEGORIES.contains(value)) {
            throw new IllegalArgumentException("category");
        }
        return value;
    }

    public static void requireMedicineBatch(String category, String lotCode, LocalDate expiresOn) {
        if ("MEDICINE".equals(category) && (lotCode == null || lotCode.isBlank() || expiresOn == null)) {
            throw new IllegalArgumentException("batch");
        }
    }

    public static BigDecimal apply(BigDecimal onHand, BigDecimal reserved, BigDecimal delta) {
        BigDecimal next = onHand.add(delta);
        if (next.compareTo(BigDecimal.ZERO) < 0 || next.compareTo(reserved) < 0) {
            throw new IllegalArgumentException("negative");
        }
        return next;
    }

    public static BigDecimal value(BigDecimal quantity, BigDecimal unitCost) {
        if (unitCost == null) {
            return null;
        }
        return quantity.multiply(unitCost);
    }
}

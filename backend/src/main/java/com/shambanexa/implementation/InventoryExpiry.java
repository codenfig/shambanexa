package com.shambanexa.implementation;

import com.shambanexa.entity.InventoryBatch;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

public final class InventoryExpiry {

    private InventoryExpiry() {
    }

    public static LocalDate useBy(InventoryBatch batch) {
        LocalDate expiry = batch.getExpiresOn();
        LocalDate opened = openedUseBy(batch);
        if (expiry == null) {
            return opened;
        }
        if (opened == null) {
            return expiry;
        }
        return expiry.isBefore(opened) ? expiry : opened;
    }

    public static LocalDate openedUseBy(InventoryBatch batch) {
        if (batch.getOpenedOn() == null || batch.getOpenDays() == null || batch.getOpenDays() < 0) {
            return null;
        }
        return batch.getOpenedOn().plusDays(batch.getOpenDays());
    }

    public static Integer daysUntil(LocalDate day, LocalDate today) {
        if (day == null || today == null) {
            return null;
        }
        return (int) ChronoUnit.DAYS.between(today, day);
    }

    public static String status(InventoryBatch batch, LocalDate today, int windowDays) {
        if ("DISPOSED".equals(batch.getStatus())) {
            return "DISPOSED";
        }
        if ("QUARANTINED".equals(batch.getStatus())) {
            return "QUARANTINED";
        }
        Integer life = daysUntil(useBy(batch), today);
        if (life != null && life < 0) {
            return "EXPIRED";
        }
        Integer best = daysUntil(batch.getBestBeforeOn(), today);
        Integer opened = daysUntil(openedUseBy(batch), today);
        if (life != null && life <= windowDays) {
            return "EXPIRING";
        }
        if (opened != null && opened >= 0 && opened <= windowDays) {
            return "OPEN_USE";
        }
        if (best != null && best >= 0 && best <= windowDays && (life == null || best < life)) {
            return "BEST_BEFORE";
        }
        return "VALID";
    }

    public static boolean usable(InventoryBatch batch, LocalDate today) {
        if (!"OPEN".equals(batch.getStatus())) {
            return false;
        }
        String state = status(batch, today, 0);
        return !"EXPIRED".equals(state) && !"DISPOSED".equals(state) && !"QUARANTINED".equals(state);
    }

    public static InventoryBatch earliest(List<InventoryBatch> batches, LocalDate today) {
        return batches.stream()
                .filter(batch -> usable(batch, today) && batch.getQuantityRemaining().signum() > 0)
                .min(Comparator.comparing(InventoryExpiry::useBy, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(InventoryBatch::getReceivedOn))
                .orElse(null);
    }
}

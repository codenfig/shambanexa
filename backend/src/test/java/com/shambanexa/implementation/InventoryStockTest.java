package com.shambanexa.implementation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InventoryStockTest {

    @Test
    void keepsKnownCategories() {
        assertEquals("EQUIPMENT", InventoryStock.category(" equipment "));
        assertThrows(IllegalArgumentException.class, () -> InventoryStock.category("TOOL"));
    }

    @Test
    void medicinesNeedABatchAndExpiry() {
        assertThrows(IllegalArgumentException.class,
                () -> InventoryStock.requireMedicineBatch("MEDICINE", " ", LocalDate.now()));
        InventoryStock.requireMedicineBatch("MEDICINE", "LSD-77", LocalDate.now());
        InventoryStock.requireMedicineBatch("FEED", null, null);
    }

    @Test
    void refusesABalanceBelowReservedOrZero() {
        assertEquals(new BigDecimal("100"), InventoryStock.apply(new BigDecimal("120"), BigDecimal.ZERO, new BigDecimal("-20")));
        assertThrows(IllegalArgumentException.class,
                () -> InventoryStock.apply(new BigDecimal("120"), new BigDecimal("110"), new BigDecimal("-20")));
        assertThrows(IllegalArgumentException.class,
                () -> InventoryStock.apply(new BigDecimal("5"), BigDecimal.ZERO, new BigDecimal("-6")));
    }

    @Test
    void valuesOnlyLinesThatHaveAUnitCost() {
        assertNull(InventoryStock.value(new BigDecimal("10"), null));
        assertEquals(new BigDecimal("25.00"), InventoryStock.value(new BigDecimal("10"), new BigDecimal("2.50")));
    }
}

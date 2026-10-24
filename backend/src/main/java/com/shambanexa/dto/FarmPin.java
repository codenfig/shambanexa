package com.shambanexa.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record FarmPin(
UUID id,
        String name,
        String region,
        String status,
        BigDecimal latitude,
        BigDecimal longitude,
        long animalCount
) {
}

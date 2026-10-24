package com.shambanexa.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AdviserPlace(
        UUID farmId,
        String farmName,
        String region,
        BigDecimal latitude,
        BigDecimal longitude
) {
}

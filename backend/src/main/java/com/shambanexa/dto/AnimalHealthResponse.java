package com.shambanexa.dto;

import java.util.UUID;

public record AnimalHealthResponse(
        UUID id,
        UUID alertId,
        UUID farmId,
        String farmName,
        String outcome,
        int animals,
        Integer herdQuantity
) {
}

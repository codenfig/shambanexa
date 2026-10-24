package com.shambanexa.dto;

import java.util.UUID;

public record BatchResponse(
UUID id,
        String code,
        String name,
        int quantity,
        String purpose,
        String status,
        UUID farmId,
        String farmName,
        String speciesName
) {
}

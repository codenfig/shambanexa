package com.shambanexa.dto;

import java.time.LocalDate;
import java.util.UUID;

public record FishCycleResponse(
UUID id,
        String code,
        String status,
        int stockedQuantity,
        int mortality,
        int harvested,
        int estimatedLiveCount,
        LocalDate startedOn,
        UUID farmId,
        String farmName
) {
}

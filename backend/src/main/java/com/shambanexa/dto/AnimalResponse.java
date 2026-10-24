package com.shambanexa.dto;

import java.time.LocalDate;
import java.util.UUID;

public record AnimalResponse(
UUID id,
        String tag,
        String name,
        String sex,
        String status,
        String healthStatus,
        LocalDate birthDate,
        String color,
        UUID farmId,
        String farmName,
        UUID speciesId,
        String speciesName,
        UUID breedId,
        String breedName,
        long version
) {
}

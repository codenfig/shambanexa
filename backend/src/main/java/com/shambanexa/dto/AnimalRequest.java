package com.shambanexa.dto;

import java.time.LocalDate;
import java.util.UUID;

public record AnimalRequest(
@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 40) String tag,
        @jakarta.validation.constraints.Size(max = 120) String name,
        @jakarta.validation.constraints.NotBlank String sex,
        @jakarta.validation.constraints.NotNull UUID speciesId,
        UUID breedId,
        @jakarta.validation.constraints.NotNull UUID farmId,
        LocalDate birthDate,
        @jakarta.validation.constraints.Size(max = 80) String color,
        String healthStatus,
        @jakarta.validation.constraints.Size(max = 80) String idempotencyKey
) {
}

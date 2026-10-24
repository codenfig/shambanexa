package com.shambanexa.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record AnimalHealthRequest(
        @NotNull UUID farmId,
        @NotBlank @Size(max = 20) String outcome,
        @NotNull @Min(1) @Max(1_000_000) Integer animals,
        @Size(max = 120) String condition,
        @Size(max = 40) String species,
        @NotBlank @Size(max = 280) String notes,
        LocalDate occurredOn,
        UUID batchId,
        UUID animalId
) {
}

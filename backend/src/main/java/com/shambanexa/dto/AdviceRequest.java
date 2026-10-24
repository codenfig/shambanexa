package com.shambanexa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdviceRequest(
        @NotBlank @Size(max = 160) String topic,
        @NotBlank @Size(max = 2000) String advice,
        @Size(max = 160) String medication,
        @Size(max = 400) String medicationNote
) {
}

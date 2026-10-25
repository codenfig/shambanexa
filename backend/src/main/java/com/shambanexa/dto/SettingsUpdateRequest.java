package com.shambanexa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SettingsUpdateRequest(
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Size(max = 40) String code,
        @NotBlank @Size(min = 3, max = 3) String currencyCode,
        @NotBlank @Size(max = 64) String timezone,
        @NotBlank @Size(max = 80) String seasonLabel,
        @NotBlank @Size(max = 16) String locale
) {
}

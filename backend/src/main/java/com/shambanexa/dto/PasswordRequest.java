package com.shambanexa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 8, max = 80) String newPassword
) {
}

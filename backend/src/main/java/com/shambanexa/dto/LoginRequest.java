package com.shambanexa.dto;

public record LoginRequest(
@jakarta.validation.constraints.Email @jakarta.validation.constraints.NotBlank String email,
        @jakarta.validation.constraints.NotBlank String password
) {
}

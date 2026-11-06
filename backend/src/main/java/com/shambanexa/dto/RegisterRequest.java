package com.shambanexa.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record RegisterRequest(
        @NotBlank @Size(max = 160) String fullName,
        @Email @NotBlank @Size(max = 180) String email,
        @NotBlank @Size(min = 8, max = 80) String password,
        @NotBlank @Size(max = 80) String region,
        @NotBlank @Size(max = 300) String address,
        @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
        @NotBlank @Size(max = 160) String farmName,
        @NotBlank @Size(max = 40) String farmCode,
        @Size(max = 40) String contactPhone,
        @Size(max = 60) List<@Valid GeoPoint> boundary
) {
}

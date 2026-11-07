package com.shambanexa.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record RegisterRequest(
        @NotBlank @Size(max = 160) String fullName,
        @Email @NotBlank @Size(max = 180) String email,
        @NotBlank @Size(min = 8, max = 80) String password,
        @Size(max = 40) String accountKind,
        @Size(max = 80) String region,
        @Size(max = 300) String address,
        BigDecimal latitude,
        BigDecimal longitude,
        @Size(max = 160) String farmName,
        @Size(max = 40) String farmCode,
        @Size(max = 40) String contactPhone,
        @Size(max = 60) List<@Valid GeoPoint> boundary,
        @Size(max = 80) String district,
        @Size(max = 160) String organizationName,
        @Size(max = 80) String licenseNumber,
        @Size(max = 120) String hours,
        @Size(max = 400) String summary,
        @Size(max = 16) String mark
) {
}

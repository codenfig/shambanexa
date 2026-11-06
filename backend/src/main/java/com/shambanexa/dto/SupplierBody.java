package com.shambanexa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SupplierBody(
        UUID id,
        @NotBlank @Size(max = 160) String name,
        @Size(max = 20) String supplierType,
        @Size(max = 40) String phone,
        @Size(max = 240) String location,
        @Size(max = 80) String registrationNo,
        @Size(max = 80) String licenseNo,
        Integer leadDays,
        @Size(max = 16) String status
) {
}

package com.shambanexa.dto;

import java.util.UUID;

public record SupplierRecord(
        UUID id,
        String name,
        String supplierType,
        String phone,
        String location,
        String registrationNo,
        String licenseNo,
        Integer leadDays,
        String status,
        int productCount
) {
}

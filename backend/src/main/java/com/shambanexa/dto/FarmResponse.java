package com.shambanexa.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record FarmResponse(
        UUID id,
        String code,
        String name,
        String region,
        String status,
        BigDecimal latitude,
        BigDecimal longitude,
        String timezone,
        String address,
        String managerName,
        String contactPhone,
        LocalDate establishedOn,
        long animalCount,
        int facilityCount,
        long version,
        Instant updatedAt,
        List<GeoPoint> boundary
) {
}

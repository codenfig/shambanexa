package com.shambanexa.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record FacilityResponse(
UUID id,
        String code,
        String name,
        String facilityType,
        String status,
        BigDecimal capacity,
        String capacityUnit,
        String supervisorName
) {
}

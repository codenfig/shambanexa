package com.shambanexa.dto;

import java.math.BigDecimal;
import java.util.List;

public record FarmRequest(
@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 40) String code,
        @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 160) String name,
        @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 80) String region,
        @jakarta.validation.constraints.NotNull BigDecimal latitude,
        @jakarta.validation.constraints.NotNull BigDecimal longitude,
        @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 300) String address,
        @jakarta.validation.constraints.Size(max = 160) String managerName,
        @jakarta.validation.constraints.Size(max = 40) String contactPhone,
        Long version,
        List<@jakarta.validation.Valid GeoPoint> boundary
) {
}

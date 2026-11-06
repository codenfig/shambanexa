package com.shambanexa.dto;

import java.math.BigDecimal;
import java.util.List;

public record FarmRequest(
@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 40) String code,
        @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 160) String name,
        @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 80) String region,
        @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("-90") @jakarta.validation.constraints.DecimalMax("90") BigDecimal latitude,
        @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("-180") @jakarta.validation.constraints.DecimalMax("180") BigDecimal longitude,
        @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 300) String address,
        @jakarta.validation.constraints.Size(max = 160) String managerName,
        @jakarta.validation.constraints.Size(max = 40) String contactPhone,
        Long version,
        @jakarta.validation.constraints.Size(max = 60) List<@jakarta.validation.Valid GeoPoint> boundary
) {
}

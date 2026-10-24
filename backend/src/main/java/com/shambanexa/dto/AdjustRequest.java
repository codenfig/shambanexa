package com.shambanexa.dto;

import java.math.BigDecimal;

public record AdjustRequest(
@jakarta.validation.constraints.NotNull BigDecimal delta,
        @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 300) String reason
) {
}

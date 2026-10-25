package com.shambanexa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record StockIssueRequest(
        @NotNull @Positive BigDecimal quantity,
        @NotBlank @Size(max = 300) String reason
) {
}

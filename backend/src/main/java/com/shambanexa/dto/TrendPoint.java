package com.shambanexa.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TrendPoint(
LocalDate month, BigDecimal milkLitres, BigDecimal revenue
) {
}

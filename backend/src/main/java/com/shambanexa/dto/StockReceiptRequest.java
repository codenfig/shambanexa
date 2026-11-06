package com.shambanexa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record StockReceiptRequest(
        @NotNull @Positive BigDecimal quantity,
        @NotBlank @Size(max = 300) String reason,
        @Size(max = 40) String lotCode,
        LocalDate expiresOn,
        @PositiveOrZero BigDecimal unitCost,
        @Size(max = 160) String supplierName,
        @Size(max = 160) String storeName,
        LocalDate manufacturedOn,
        LocalDate bestBeforeOn,
        LocalDate receivedOn,
        @Size(max = 40) String purchaseOrder,
        @Size(max = 20) String inspection,
        Integer openDays,
        Boolean allowExpired,
        LocalDate openedOn,
        List<BatchLine> lines
) {
    public record BatchLine(
            @NotBlank @Size(max = 40) String lotCode,
            @NotNull @Positive BigDecimal quantity,
            LocalDate manufacturedOn,
            LocalDate expiresOn,
            LocalDate bestBeforeOn,
            @PositiveOrZero BigDecimal unitCost,
            @Size(max = 160) String storeName
    ) {
    }
}

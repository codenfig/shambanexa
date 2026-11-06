package com.shambanexa.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InventoryRequest(
@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 40) String sku,
        @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 160) String name,
        @jakarta.validation.constraints.NotBlank String category,
        @jakarta.validation.constraints.NotNull UUID farmId,
        @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.PositiveOrZero BigDecimal quantityOnHand,
        @jakarta.validation.constraints.NotBlank String unit,
        @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.PositiveOrZero BigDecimal reorderPoint,
        String lotCode,
        LocalDate expiresOn,
        @jakarta.validation.constraints.PositiveOrZero BigDecimal unitCost,
        @jakarta.validation.constraints.Size(max = 160) String supplierName,
        @jakarta.validation.constraints.Size(max = 160) String storeName,
        @jakarta.validation.constraints.Size(max = 400) String description,
        @jakarta.validation.constraints.Size(max = 160) String brand,
        java.util.UUID productTypeId,
        @jakarta.validation.constraints.PositiveOrZero java.math.BigDecimal reorderQuantity,
        @jakarta.validation.constraints.PositiveOrZero java.math.BigDecimal sellingPrice,
        @jakarta.validation.constraints.Size(max = 160) String genericName,
        java.util.UUID medicineClassId,
        @jakarta.validation.constraints.Size(max = 80) String dosageForm,
        @jakarta.validation.constraints.Size(max = 80) String strength,
        @jakarta.validation.constraints.Size(max = 160) String speciesLabel,
        Boolean prescriptionRequired,
        @jakarta.validation.constraints.Size(max = 240) String storageNote,
        @jakarta.validation.constraints.Size(max = 80) String registrationNo,
        Integer withdrawalDays,
        @jakarta.validation.constraints.Size(max = 400) String safetyNote,
        java.util.UUID feedTypeId,
        @jakarta.validation.constraints.Size(max = 80) String growthStage,
        @jakarta.validation.constraints.Size(max = 80) String packSize,
        @jakarta.validation.constraints.Size(max = 400) String formulation,
        java.time.LocalDate manufacturedOn,
        java.time.LocalDate bestBeforeOn,
        Integer openDays
) {
}

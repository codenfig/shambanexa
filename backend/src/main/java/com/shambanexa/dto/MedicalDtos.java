package com.shambanexa.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class MedicalDtos {
    private MedicalDtos() {
    }

    public record Board(
            List<SupplierView> suppliers,
            List<String> regions,
            List<String> districts,
            SupplierView ownShop,
            List<SupplierView> pendingReview,
            List<RequestView> requests,
            boolean administrator,
            boolean adviser
    ) {
    }

    public record SupplierView(
            UUID id,
            String name,
            String mark,
            String region,
            String district,
            String address,
            String phone,
            String hours,
            double latitude,
            double longitude,
            String licenseNumber,
            String licenseAuthority,
            LocalDate licenseExpiresOn,
            String productsOffered,
            String summary,
            String approval,
            String shopState,
            boolean owned,
            List<MedicineView> medicines
    ) {
    }

    public record MedicineView(
            UUID id,
            String name,
            String animalTypes,
            String category,
            BigDecimal priceAmount,
            String currency,
            BigDecimal quantity,
            String unit,
            String stock,
            boolean prescriptionRequired,
            String summary
    ) {
    }

    public record RequestView(
            UUID id,
            UUID supplierId,
            String supplierName,
            UUID medicineId,
            String medicineName,
            BigDecimal quantity,
            String unit,
            String fulfillment,
            String status,
            boolean prescriptionRequired,
            String requesterName,
            UUID farmId,
            String farmName,
            String requesterNote,
            String adviserName,
            String adviserNote,
            String supplierNote,
            boolean ownedShop,
            boolean ownRequest,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record ShopBody(
            String name,
            String mark,
            String region,
            String district,
            String address,
            String phone,
            String hours,
            Double latitude,
            Double longitude,
            String licenseNumber,
            String licenseAuthority,
            LocalDate licenseExpiresOn,
            String productsOffered,
            String summary,
            String shopState
    ) {
    }

    public record MedicineBody(
            String name,
            String animalTypes,
            String category,
            BigDecimal priceAmount,
            String currency,
            BigDecimal quantity,
            String unit,
            String stock,
            Boolean prescriptionRequired,
            String summary
    ) {
    }

    public record OrderBody(
            UUID supplierId,
            UUID medicineId,
            String medicineName,
            BigDecimal quantity,
            String unit,
            String fulfillment,
            UUID farmId,
            Boolean prescriptionRequired,
            String note
    ) {
    }

    public record NoteBody(Boolean approve, String action, String note) {
    }
}

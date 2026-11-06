package com.shambanexa.entity;

import com.shambanexa.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "inventory_items")
@Getter
@Setter
public class InventoryItem extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID farmId;

    @Column(nullable = false, length = 40)
    private String sku;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 40)
    private String category;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal quantityOnHand;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal reservedQuantity;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal reorderPoint;

    @Column(precision = 14, scale = 2)
    private BigDecimal unitCost;

    @Column(length = 160)
    private String supplierName;

    @Column(length = 160)
    private String storeName;

    @Column(length = 40)
    private String lotCode;

    private LocalDate expiresOn;

    private UUID productTypeId;

    @Column(length = 400)
    private String description;

    @Column(length = 160)
    private String brand;

    @Column(precision = 14, scale = 2)
    private BigDecimal reorderQuantity;

    @Column(precision = 14, scale = 2)
    private BigDecimal sellingPrice;

    private UUID supplierId;

    @Column(length = 160)
    private String genericName;

    private UUID medicineClassId;

    @Column(length = 80)
    private String dosageForm;

    @Column(length = 80)
    private String strength;

    @Column(length = 160)
    private String speciesLabel;

    private Boolean prescriptionRequired;

    @Column(length = 240)
    private String storageNote;

    @Column(length = 80)
    private String registrationNo;

    private Integer withdrawalDays;

    @Column(length = 400)
    private String safetyNote;

    private UUID feedTypeId;

    @Column(length = 80)
    private String growthStage;

    @Column(length = 80)
    private String packSize;

    @Column(length = 400)
    private String formulation;

    private LocalDate manufacturedOn;

    private LocalDate bestBeforeOn;

    private Integer openDays;

    @Column(nullable = false)
    private boolean perishable;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (reservedQuantity == null) {
            reservedQuantity = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}

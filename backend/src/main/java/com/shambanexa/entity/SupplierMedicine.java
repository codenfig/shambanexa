package com.shambanexa.entity;

import com.shambanexa.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "supplier_medicine")
@Getter
@Setter
public class SupplierMedicine extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID supplierId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 240)
    private String animalTypes;

    @Column(nullable = false, length = 80)
    private String category;

    @Column(length = 240)
    private String summary;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal priceAmount;

    @Column(nullable = false, length = 3)
    private String currencyCode;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal quantity;

    @Column(nullable = false, length = 40)
    private String unitLabel;

    @Column(nullable = false, length = 20)
    private String stockState;

    @Column(nullable = false)
    private boolean prescriptionRequired;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}

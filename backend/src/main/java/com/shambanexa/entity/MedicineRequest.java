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
@Table(name = "medicine_request")
@Getter
@Setter
public class MedicineRequest extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID supplierId;

    private UUID medicineId;

    @Column(nullable = false)
    private UUID requesterId;

    private UUID farmId;

    @Column(nullable = false, length = 160)
    private String medicineName;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal quantity;

    @Column(nullable = false, length = 40)
    private String unitLabel;

    @Column(nullable = false, length = 20)
    private String fulfillment;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(nullable = false)
    private boolean prescriptionRequired;

    @Column(nullable = false)
    private boolean stockDrawn;

    @Column(length = 400)
    private String requesterNote;

    private UUID adviserId;

    @Column(length = 400)
    private String adviserNote;

    @Column(length = 400)
    private String supplierNote;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }
}

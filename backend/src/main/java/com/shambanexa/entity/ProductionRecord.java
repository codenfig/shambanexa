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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "production_records")
@Getter
@Setter
public class ProductionRecord extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID farmId;

    @Column(nullable = false, length = 40)
    private String productType;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal quantity;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(nullable = false)
    private LocalDate recordedOn;

    @Column(length = 300)
    private String notes;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}

package com.shambanexa.entity;

import com.shambanexa.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "treatment_doses")
@Getter
@Setter
public class TreatmentDose extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID treatmentId;

    @Column(nullable = false)
    private UUID itemId;

    private UUID batchId;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    private Integer withdrawalDays;

    private LocalDate withdrawalEndsOn;

    @Column(nullable = false)
    private boolean issued;

    @Column(nullable = false)
    private Instant createdAt;
}

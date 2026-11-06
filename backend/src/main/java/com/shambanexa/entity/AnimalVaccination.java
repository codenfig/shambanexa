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
@Table(name = "animal_vaccinations")
@Getter
@Setter
public class AnimalVaccination extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID farmId;

    private UUID animalId;

    private UUID herdId;

    private UUID protocolId;

    @Column(nullable = false, length = 80)
    private String speciesName;

    @Column(length = 80)
    private String ageGroup;

    @Column(nullable = false, length = 160)
    private String vaccineName;

    private UUID itemId;

    private UUID batchId;

    @Column(precision = 14, scale = 3)
    private BigDecimal quantity;

    private Integer withdrawalDays;

    private LocalDate withdrawalEndsOn;

    private LocalDate administeredOn;

    private LocalDate nextDueOn;

    @Column(nullable = false, length = 120)
    private String veterinarianName;

    @Column(length = 400)
    private String notes;

    @Column(name = "vaccination_status", nullable = false, length = 20)
    private String vaccinationStatus;

    @Column(nullable = false)
    private boolean doseIssued;

    @Column(nullable = false)
    private UUID createdBy;

    @Column(nullable = false)
    private Instant createdAt;
}

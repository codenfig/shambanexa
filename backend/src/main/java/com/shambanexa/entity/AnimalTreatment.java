package com.shambanexa.entity;

import com.shambanexa.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "animal_treatments")
@Getter
@Setter
public class AnimalTreatment extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID farmId;

    private UUID animalId;

    private UUID herdId;

    @Column(nullable = false, length = 500)
    private String symptoms;

    @Column(nullable = false, length = 240)
    private String diagnosis;

    @Column(nullable = false, length = 800)
    private String description;

    @Column(nullable = false, length = 120)
    private String veterinarianName;

    @Column(name = "treatment_status", nullable = false, length = 20)
    private String treatmentStatus;

    @Column(nullable = false)
    private LocalDate startedOn;

    @Column(length = 240)
    private String outcome;

    private LocalDate followUpOn;

    @Column(nullable = false)
    private UUID createdBy;

    @Column(nullable = false)
    private Instant createdAt;
}

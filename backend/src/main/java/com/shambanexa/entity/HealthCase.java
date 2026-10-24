package com.shambanexa.entity;

import com.shambanexa.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "health_cases")
@Getter
@Setter
public class HealthCase extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID farmId;

    private UUID animalId;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 40)
    private String caseStatus;

    @Column(nullable = false, length = 20)
    private String severity;

    @Column(nullable = false)
    private LocalDate openedOn;

    @Column(length = 400)
    private String notes;
}

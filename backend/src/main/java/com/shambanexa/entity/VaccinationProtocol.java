package com.shambanexa.entity;

import com.shambanexa.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vaccination_protocols")
@Getter
@Setter
public class VaccinationProtocol extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 80)
    private String speciesName;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 80)
    private String ageGroup;

    @Column(nullable = false)
    private int intervalDays;

    private UUID itemId;

    @Column(length = 400)
    private String notes;

    @Column(nullable = false)
    private UUID approvedBy;

    @Column(nullable = false, length = 120)
    private String approvedByName;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private Instant createdAt;
}

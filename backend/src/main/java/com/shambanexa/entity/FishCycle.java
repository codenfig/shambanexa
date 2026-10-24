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
@Table(name = "fish_cycles")
@Getter
@Setter
public class FishCycle extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID farmId;

    private UUID facilityId;

    @Column(nullable = false)
    private UUID speciesId;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(nullable = false)
    private Integer stockedQuantity;

    @Column(nullable = false)
    private Integer mortality;

    @Column(nullable = false)
    private Integer harvested;

    @Column(nullable = false)
    private LocalDate startedOn;

    private LocalDate closedOn;
}

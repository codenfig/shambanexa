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
@Table(name = "species")
@Getter
@Setter
public class Species extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 40)
    private String category;

    @Column(nullable = false, length = 16)
    private String chartColor;

    @Column(nullable = false)
    private Integer sortOrder;

    @Column(nullable = false)
    private boolean individualTracking;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(nullable = false, length = 16)
    private String origin;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private UUID createdBy;

    private UUID updatedBy;
}

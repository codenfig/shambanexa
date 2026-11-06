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
@Table(name = "inventory_references")
@Getter
@Setter
public class InventoryReference extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 40)
    private String kind;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    private UUID parentId;

    @Column(length = 400)
    private String detail;

    private Integer numericValue;

    @Column(nullable = false)
    private Integer sortOrder = 0;

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

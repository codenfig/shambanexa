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
@Table(name = "inventory_ledger")
@Getter
@Setter
public class InventoryLedgerEntry extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID itemId;

    @Column(nullable = false)
    private UUID farmId;

    @Column(nullable = false, length = 20)
    private String movementType;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal quantityDelta;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal quantityAfter;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(nullable = false, length = 300)
    private String reason;

    @Column(length = 40)
    private String lotCode;

    private LocalDate expiresOn;

    @Column(length = 160)
    private String supplierName;

    @Column(length = 160)
    private String storeName;

    private UUID relatedId;

    private UUID batchId;

    private UUID actorId;

    @Column(nullable = false, length = 160)
    private String actorName;

    @Column(nullable = false)
    private Instant occurredAt;
}

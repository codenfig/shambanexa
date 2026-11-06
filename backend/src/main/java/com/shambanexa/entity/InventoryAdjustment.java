package com.shambanexa.entity;

import com.shambanexa.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_adjustment_requests")
@Getter
@Setter
public class InventoryAdjustment extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID itemId;

    @Column(nullable = false)
    private UUID farmId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal delta;

    @Column(nullable = false, length = 300)
    private String reason;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private UUID requestedBy;

    @Column(nullable = false, length = 160)
    private String requestedByName;

    @Column(nullable = false)
    private Instant requestedAt;

    private UUID decidedBy;

    @Column(length = 160)
    private String decidedByName;

    private Instant decidedAt;

    private UUID ledgerId;

    private UUID batchId;

    @Column(length = 40)
    private String reasonCode;

    @Column(length = 400)
    private String notes;

    @Column(precision = 14, scale = 2)
    private BigDecimal quantityBefore;
}

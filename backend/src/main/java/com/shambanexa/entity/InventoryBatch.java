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
@Table(name = "inventory_batches")
@Getter
@Setter
public class InventoryBatch extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID itemId;

    @Column(nullable = false)
    private UUID farmId;

    @Column(nullable = false, length = 40)
    private String lotCode;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal quantityReceived;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal quantityRemaining;

    private LocalDate manufacturedOn;

    @Column(nullable = false)
    private LocalDate receivedOn;

    private LocalDate expiresOn;

    private LocalDate bestBeforeOn;

    private LocalDate openedOn;

    private Integer openDays;

    private UUID supplierId;

    @Column(length = 160)
    private String supplierName;

    @Column(length = 160)
    private String storeName;

    @Column(precision = 14, scale = 2)
    private BigDecimal unitCost;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private Instant createdAt;
}

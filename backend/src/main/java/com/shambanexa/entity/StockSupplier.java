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
@Table(name = "stock_suppliers")
@Getter
@Setter
public class StockSupplier extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 20)
    private String supplierType;

    @Column(length = 40)
    private String phone;

    @Column(length = 240)
    private String location;

    @Column(length = 80)
    private String registrationNo;

    @Column(length = 80)
    private String licenseNo;

    private Integer leadDays;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;
}

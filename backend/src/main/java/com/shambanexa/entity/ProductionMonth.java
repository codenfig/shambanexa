package com.shambanexa.entity;

import com.shambanexa.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "production_monthly")
@Getter
@Setter
public class ProductionMonth extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private LocalDate yearMonth;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal milkLitres;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal revenueAmount;

    @Column(nullable = false, length = 3)
    private String currencyCode;
}

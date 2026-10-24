package com.shambanexa.entity;

import com.shambanexa.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "financial_entries")
@Getter
@Setter
public class FinancialEntry extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    private UUID farmId;

    @Column(nullable = false, length = 40)
    private String entryType;

    @Column(nullable = false, length = 80)
    private String category;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currencyCode;

    @Column(nullable = false)
    private LocalDate recordedOn;

    @Column(nullable = false, length = 300)
    private String description;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}

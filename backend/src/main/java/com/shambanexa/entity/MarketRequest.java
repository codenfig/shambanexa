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
@Table(name = "market_request")
@Getter
@Setter
public class MarketRequest extends BaseEntity {
    @Column(nullable = false) private UUID tenantId;
    @Column(nullable = false) private UUID buyerId;
    private UUID productId;
    @Column(nullable = false, length = 160) private String productName;
    @Column(nullable = false, length = 40) private String category;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal quantity;
    @Column(nullable = false, length = 16) private String unitLabel;
    @Column(nullable = false, length = 80) private String grade;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal targetPrice;
    @Column(nullable = false, length = 3) private String currencyCode;
    @Column(nullable = false, length = 80) private String region;
    @Column(nullable = false, length = 80) private String district;
    @Column(precision = 9, scale = 6) private BigDecimal latitude;
    @Column(precision = 9, scale = 6) private BigDecimal longitude;
    @Column(nullable = false) private LocalDate neededOn;
    @Column(length = 400) private String note;
    @Column(nullable = false, length = 32) private String status;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }
}

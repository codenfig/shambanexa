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
import java.util.UUID;

@Entity
@Table(name = "market_product")
@Getter
@Setter
public class MarketProduct extends BaseEntity {
    @Column(nullable = false) private UUID tenantId;
    @Column(nullable = false) private UUID sellerId;
    @Column(nullable = false, length = 160) private String name;
    @Column(nullable = false, length = 40) private String category;
    @Column(nullable = false, length = 20) private String imageKey;
    @Column(nullable = false, length = 800) private String description;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal quantity;
    @Column(nullable = false, length = 16) private String unitLabel;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal priceAmount;
    @Column(nullable = false, length = 3) private String currencyCode;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal minOrder;
    @Column(nullable = false, length = 80) private String grade;
    @Column(nullable = false, length = 80) private String region;
    @Column(nullable = false, length = 80) private String district;
    @Column(nullable = false, precision = 9, scale = 6) private BigDecimal latitude;
    @Column(nullable = false, precision = 9, scale = 6) private BigDecimal longitude;
    @Column(nullable = false, length = 20) private String fulfillment;
    @Column(nullable = false, length = 20) private String status;
    @Column(nullable = false) private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}

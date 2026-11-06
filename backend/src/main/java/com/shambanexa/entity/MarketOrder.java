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
@Table(name = "market_order")
@Getter
@Setter
public class MarketOrder extends BaseEntity {
    @Column(nullable = false) private UUID tenantId;
    @Column(nullable = false) private UUID requestId;
    @Column(nullable = false) private UUID offerId;
    @Column(nullable = false) private UUID buyerId;
    @Column(nullable = false) private UUID sellerId;
    private UUID productId;
    @Column(nullable = false, length = 160) private String productName;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal quantity;
    @Column(nullable = false, length = 16) private String unitLabel;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal priceAmount;
    @Column(nullable = false, length = 3) private String currencyCode;
    @Column(nullable = false, length = 20) private String fulfillment;
    @Column(nullable = false, length = 32) private String status;
    @Column(nullable = false) private boolean stockDrawn;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }
}

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
@Table(name = "market_seller")
@Getter
@Setter
public class MarketSeller extends BaseEntity {
    @Column(nullable = false) private UUID tenantId;
    @Column(nullable = false) private UUID ownerId;
    @Column(nullable = false, length = 160) private String name;
    @Column(nullable = false, length = 80) private String region;
    @Column(nullable = false, length = 80) private String district;
    @Column(nullable = false, length = 40) private String phone;
    @Column(nullable = false, precision = 9, scale = 6) private BigDecimal latitude;
    @Column(nullable = false, precision = 9, scale = 6) private BigDecimal longitude;
    @Column(nullable = false, length = 400) private String summary;
    @Column(nullable = false, length = 20) private String approval;
    @Column(nullable = false) private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}

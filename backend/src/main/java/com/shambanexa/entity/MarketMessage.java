package com.shambanexa.entity;

import com.shambanexa.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "market_message")
@Getter
@Setter
public class MarketMessage extends BaseEntity {
    @Column(nullable = false) private UUID tenantId;
    private UUID requestId;
    private UUID orderId;
    @Column(nullable = false) private UUID senderId;
    @Column(nullable = false, length = 1000) private String body;
    @Column(nullable = false) private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}

package com.shambanexa.repository;

import com.shambanexa.entity.MarketReview;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarketReviewRepository extends JpaRepository<MarketReview, UUID> {
    List<MarketReview> findByTenantId(UUID tenantId);
    Optional<MarketReview> findByTenantIdAndOrderId(UUID tenantId, UUID orderId);
}

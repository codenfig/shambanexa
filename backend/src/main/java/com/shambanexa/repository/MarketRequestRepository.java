package com.shambanexa.repository;

import com.shambanexa.entity.MarketRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarketRequestRepository extends JpaRepository<MarketRequest, UUID> {
    List<MarketRequest> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<MarketRequest> findByTenantIdAndId(UUID tenantId, UUID id);
}

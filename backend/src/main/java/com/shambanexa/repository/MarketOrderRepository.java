package com.shambanexa.repository;

import com.shambanexa.entity.MarketOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarketOrderRepository extends JpaRepository<MarketOrder, UUID> {
    List<MarketOrder> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<MarketOrder> findByTenantIdAndId(UUID tenantId, UUID id);
}

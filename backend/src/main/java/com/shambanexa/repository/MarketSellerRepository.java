package com.shambanexa.repository;

import com.shambanexa.entity.MarketSeller;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarketSellerRepository extends JpaRepository<MarketSeller, UUID> {
    List<MarketSeller> findByTenantIdOrderByNameAsc(UUID tenantId);
    Optional<MarketSeller> findByTenantIdAndId(UUID tenantId, UUID id);
    Optional<MarketSeller> findByTenantIdAndOwnerId(UUID tenantId, UUID ownerId);
}

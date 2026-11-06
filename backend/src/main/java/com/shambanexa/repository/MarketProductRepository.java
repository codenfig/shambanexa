package com.shambanexa.repository;

import com.shambanexa.entity.MarketProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarketProductRepository extends JpaRepository<MarketProduct, UUID> {
    List<MarketProduct> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<MarketProduct> findByTenantIdAndId(UUID tenantId, UUID id);
}

package com.shambanexa.repository;

import com.shambanexa.entity.MarketOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarketOfferRepository extends JpaRepository<MarketOffer, UUID> {
    List<MarketOffer> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<MarketOffer> findByTenantIdAndId(UUID tenantId, UUID id);
}

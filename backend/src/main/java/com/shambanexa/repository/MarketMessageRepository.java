package com.shambanexa.repository;

import com.shambanexa.entity.MarketMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface MarketMessageRepository extends JpaRepository<MarketMessage, UUID> {
    List<MarketMessage> findByTenantIdOrderByCreatedAtAsc(UUID tenantId);
}

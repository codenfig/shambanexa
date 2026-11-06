package com.shambanexa.repository;

import com.shambanexa.entity.MarketNotice;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface MarketNoticeRepository extends JpaRepository<MarketNotice, UUID> {
    List<MarketNotice> findByTenantIdAndUserIdOrderByCreatedAtDesc(UUID tenantId, UUID userId);
}

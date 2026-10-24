package com.shambanexa.repository;

import com.shambanexa.entity.ProductionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProductionRecordRepository extends JpaRepository<ProductionRecord, UUID> {
    List<ProductionRecord> findByTenantIdOrderByRecordedOnDesc(UUID tenantId);

    @Query("""
            select coalesce(sum(p.quantity), 0)
            from ProductionRecord p
            where p.tenantId = :tenantId
              and p.productType = 'MILK'
              and p.recordedOn = :day
              and (:allFarms = true or p.farmId in :farmIds)
            """)
    BigDecimal sumMilk(UUID tenantId, LocalDate day, boolean allFarms, Collection<UUID> farmIds);
}

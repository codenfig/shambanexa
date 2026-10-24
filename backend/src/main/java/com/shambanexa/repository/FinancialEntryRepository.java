package com.shambanexa.repository;

import com.shambanexa.entity.FinancialEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.UUID;

public interface FinancialEntryRepository extends JpaRepository<FinancialEntry, UUID> {
    @Query("""
            select coalesce(sum(f.amount), 0)
            from FinancialEntry f
            where f.tenantId = :tenantId
              and f.entryType = 'INCOME'
              and f.recordedOn >= :from
              and f.recordedOn < :to
              and (:allFarms = true or f.farmId in :farmIds)
            """)
    BigDecimal sumIncome(UUID tenantId, LocalDate from, LocalDate to, boolean allFarms, Collection<UUID> farmIds);
}

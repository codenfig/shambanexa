package com.shambanexa.repository;

import com.shambanexa.entity.MovementEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MovementEventRepository extends JpaRepository<MovementEvent, UUID> {

    List<MovementEvent> findByTenantIdOrderByEffectiveAtDesc(UUID tenantId);

    boolean existsByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    java.util.Optional<MovementEvent> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    @Query("""
            select coalesce(sum(m.quantity), 0)
            from MovementEvent m
            where m.tenantId = :tenantId
              and m.subjectType in ('ANIMAL', 'BATCH')
              and (:allFarms = true or m.farmId in :farmIds)
            """)
    long sumLedger(UUID tenantId, boolean allFarms, Collection<UUID> farmIds);
}

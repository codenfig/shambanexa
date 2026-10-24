package com.shambanexa.repository;

import com.shambanexa.entity.FishCycle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FishCycleRepository extends JpaRepository<FishCycle, UUID> {
    List<FishCycle> findByTenantIdOrderByStartedOnDesc(UUID tenantId);
}

package com.shambanexa.repository;

import com.shambanexa.entity.PopulationSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface PopulationSnapshotRepository extends JpaRepository<PopulationSnapshot, UUID> {
    Optional<PopulationSnapshot> findFirstByTenantIdAndAsOfDateLessThanOrderByAsOfDateDesc(UUID tenantId, LocalDate date);
}

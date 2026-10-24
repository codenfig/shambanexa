package com.shambanexa.repository;

import com.shambanexa.entity.Species;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpeciesRepository extends JpaRepository<Species, UUID> {
    List<Species> findByTenantIdOrderBySortOrderAsc(UUID tenantId);

    Optional<Species> findByIdAndTenantId(UUID id, UUID tenantId);
}

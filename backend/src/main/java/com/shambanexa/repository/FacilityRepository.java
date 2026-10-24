package com.shambanexa.repository;

import com.shambanexa.entity.Facility;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface FacilityRepository extends JpaRepository<Facility, UUID> {
    List<Facility> findByTenantIdAndFarmIdOrderByNameAsc(UUID tenantId, UUID farmId);

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndFarmIdIn(UUID tenantId, Collection<UUID> farmIds);
}

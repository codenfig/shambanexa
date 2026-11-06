package com.shambanexa.repository;

import com.shambanexa.entity.Breed;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BreedRepository extends JpaRepository<Breed, UUID> {
    List<Breed> findByTenantIdAndSpeciesIdOrderByNameAsc(UUID tenantId, UUID speciesId);

    Optional<Breed> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Breed> findByTenantIdOrderByNameAsc(UUID tenantId);

    long countByTenantIdAndSpeciesId(UUID tenantId, UUID speciesId);

    Optional<Breed> findByTenantIdAndSpeciesIdAndCodeIgnoreCase(UUID tenantId, UUID speciesId, String code);

    Optional<Breed> findByTenantIdAndSpeciesIdAndNameIgnoreCase(UUID tenantId, UUID speciesId, String name);
}

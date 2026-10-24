package com.shambanexa.repository;

import com.shambanexa.entity.Breed;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BreedRepository extends JpaRepository<Breed, UUID> {
    List<Breed> findByTenantIdAndSpeciesIdOrderByNameAsc(UUID tenantId, UUID speciesId);

    Optional<Breed> findByIdAndTenantId(UUID id, UUID tenantId);
}

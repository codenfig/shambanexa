package com.shambanexa.repository;

import com.shambanexa.entity.Farm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FarmRepository extends JpaRepository<Farm, UUID> {
    List<Farm> findByTenantIdOrderByNameAsc(UUID tenantId);

    Optional<Farm> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);
}

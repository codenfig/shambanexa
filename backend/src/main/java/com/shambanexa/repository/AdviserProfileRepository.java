package com.shambanexa.repository;

import com.shambanexa.entity.AdviserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdviserProfileRepository extends JpaRepository<AdviserProfile, UUID> {
    List<AdviserProfile> findByTenantId(UUID tenantId);

    Optional<AdviserProfile> findByTenantIdAndUserId(UUID tenantId, UUID userId);
}

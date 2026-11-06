package com.shambanexa.repository;

import com.shambanexa.entity.AccessRoleDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccessRoleDefinitionRepository extends JpaRepository<AccessRoleDefinition, UUID> {
    List<AccessRoleDefinition> findByTenantIdOrderByLabelAsc(UUID tenantId);

    Optional<AccessRoleDefinition> findByTenantIdAndCode(UUID tenantId, String code);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);
}

package com.shambanexa.repository;

import com.shambanexa.entity.UserRoleGrant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserRoleGrantRepository extends JpaRepository<UserRoleGrant, UUID> {
    List<UserRoleGrant> findByUserId(UUID userId);

    List<UserRoleGrant> findByTenantId(UUID tenantId);
}

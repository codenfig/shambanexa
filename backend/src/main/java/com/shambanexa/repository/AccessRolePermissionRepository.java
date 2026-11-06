package com.shambanexa.repository;

import com.shambanexa.entity.AccessRolePermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AccessRolePermissionRepository extends JpaRepository<AccessRolePermission, UUID> {
    List<AccessRolePermission> findByRoleIdIn(Collection<UUID> roleIds);

    void deleteByRoleId(UUID roleId);
}

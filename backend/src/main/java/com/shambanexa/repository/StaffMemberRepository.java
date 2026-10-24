package com.shambanexa.repository;

import com.shambanexa.entity.StaffMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StaffMemberRepository extends JpaRepository<StaffMember, UUID> {
    List<StaffMember> findByTenantIdOrderByFullNameAsc(UUID tenantId);

    List<StaffMember> findTop8ByTenantIdAndFullNameContainingIgnoreCase(UUID tenantId, String name);
}

package com.shambanexa.repository;

import com.shambanexa.entity.StaffCompetency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffCompetencyRepository extends JpaRepository<StaffCompetency, UUID> {
    List<StaffCompetency> findByTenantIdOrderBySkillAsc(UUID tenantId);

    Optional<StaffCompetency> findByTenantIdAndStaffIdAndSkillIgnoreCase(UUID tenantId, UUID staffId, String skill);
}

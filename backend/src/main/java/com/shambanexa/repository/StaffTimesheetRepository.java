package com.shambanexa.repository;

import com.shambanexa.entity.StaffTimesheet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffTimesheetRepository extends JpaRepository<StaffTimesheet, UUID> {
    List<StaffTimesheet> findByTenantIdOrderByWorkDateDesc(UUID tenantId);

    Optional<StaffTimesheet> findByTenantIdAndStaffIdAndWorkDate(UUID tenantId, UUID staffId, LocalDate workDate);
}

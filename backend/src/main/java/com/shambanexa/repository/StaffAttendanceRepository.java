package com.shambanexa.repository;

import com.shambanexa.entity.StaffAttendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffAttendanceRepository extends JpaRepository<StaffAttendance, UUID> {
    List<StaffAttendance> findByTenantIdOrderByWorkDateDesc(UUID tenantId);

    Optional<StaffAttendance> findByTenantIdAndStaffIdAndWorkDate(UUID tenantId, UUID staffId, LocalDate workDate);
}

package com.shambanexa.repository;

import com.shambanexa.entity.StaffShift;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffShiftRepository extends JpaRepository<StaffShift, UUID> {
    List<StaffShift> findByTenantIdOrderByWeekdayAsc(UUID tenantId);

    Optional<StaffShift> findByTenantIdAndStaffIdAndWeekday(UUID tenantId, UUID staffId, short weekday);
}

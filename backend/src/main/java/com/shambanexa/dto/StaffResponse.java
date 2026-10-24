package com.shambanexa.dto;

import java.time.LocalDate;
import java.util.UUID;

public record StaffResponse(
UUID id,
        String fullName,
        String roleTitle,
        UUID farmId,
        String farmName,
        String phone,
        String status,
        LocalDate hiredOn
) {
}

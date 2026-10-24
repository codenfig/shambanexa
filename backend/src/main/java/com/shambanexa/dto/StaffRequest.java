package com.shambanexa.dto;

import java.time.LocalDate;
import java.util.UUID;

public record StaffRequest(
@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 160) String fullName,
        @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 80) String roleTitle,
        @jakarta.validation.constraints.NotNull UUID farmId,
        @jakarta.validation.constraints.Size(max = 40) String phone,
        LocalDate hiredOn
) {
}

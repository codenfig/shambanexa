package com.shambanexa.dto;

import java.time.LocalDate;
import java.util.UUID;

public record TaskResponse(
UUID id,
        String title,
        String taskType,
        String status,
        LocalDate dueOn,
        String assigneeName,
        String notes,
        UUID farmId,
        String farmName
) {
}

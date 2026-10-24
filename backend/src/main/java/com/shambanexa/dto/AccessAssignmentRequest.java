package com.shambanexa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record AccessAssignmentRequest(
        @NotBlank @Size(max = 40) String roleCode,
        @NotBlank @Size(max = 20) String accessScope,
        List<UUID> farmIds
) {
}

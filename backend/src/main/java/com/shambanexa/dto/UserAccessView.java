package com.shambanexa.dto;

import java.util.List;
import java.util.UUID;

public record UserAccessView(
        UUID userId,
        String fullName,
        String email,
        String roleCode,
        String roleLabel,
        String accessScope,
        List<UUID> farmIds,
        List<String> farmNames
) {
}

package com.shambanexa.common;

import java.util.Set;
import java.util.UUID;

public record CurrentUser(
        UUID id,
        UUID tenantId,
        String email,
        String fullName,
        String jobTitle,
        String roleLabel,
        boolean allFarms,
        Set<UUID> farmIds,
        Set<String> authorities,
        String accessScope,
        boolean canManageAccess,
        boolean canWriteOperations,
        boolean canAdvise
) {
}

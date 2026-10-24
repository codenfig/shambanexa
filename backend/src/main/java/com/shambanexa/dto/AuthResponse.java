package com.shambanexa.dto;

import java.util.Set;
import java.util.UUID;
import com.shambanexa.common.CurrentUser;

public record AuthResponse(
String token,
        UUID userId,
        String email,
        String fullName,
        String jobTitle,
        String roleLabel,
        boolean allFarms,
        Set<UUID> farmIds,
        String accessScope,
        boolean canManageAccess,
        boolean canWriteOperations,
        boolean canAdvise
) {
public static AuthResponse from(CurrentUser user, String token) {
    return new AuthResponse(
            token,
            user.id(),
            user.email(),
            user.fullName(),
            user.jobTitle(),
            user.roleLabel(),
            user.allFarms(),
            user.farmIds(),
            user.accessScope(),
            user.canManageAccess(),
            user.canWriteOperations(),
            user.canAdvise()
    );
    }
}

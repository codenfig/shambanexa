package com.shambanexa.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AccessCatalogResponse(
        List<RoleCard> roles,
        List<AuditRow> activity
) {
    public record RoleCard(
            String code,
            String label,
            String description,
            boolean systemRole,
            String scope,
            String scopeLabel,
            int userCount,
            boolean canManageUsers,
            boolean requireApproval,
            List<ModuleGrant> modules,
            List<AssignedUser> users
    ) {
    }

    public record ModuleGrant(
            String id,
            String label,
            boolean view,
            boolean create,
            boolean edit,
            boolean delete,
            boolean approve,
            boolean export,
            String basis
    ) {
    }

    public record AssignedUser(
            UUID id,
            String fullName,
            String email,
            String jobTitle,
            String status,
            List<String> farms
    ) {
    }

    public record AuditRow(
            UUID id,
            String action,
            String entityType,
            String detail,
            String actorName,
            Instant recordedAt
    ) {
    }
}

package com.shambanexa.security;

import com.shambanexa.common.ApiException;
import com.shambanexa.common.CurrentUser;
import com.shambanexa.common.RolePermissions;
import com.shambanexa.common.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Component
public class AccessGuard {

    public static final UUID ALL_FARMS_SENTINEL = UUID.fromString("00000000-0000-0000-0000-000000000000");

    public CurrentUser current() {
        return TenantContext.require();
    }

    public UUID tenantId() {
        return current().tenantId();
    }

    public boolean allFarms() {
        return current().allFarms();
    }

    public Collection<UUID> farmScope() {
        if (allFarms() || current().farmIds().isEmpty()) {
            return List.of(ALL_FARMS_SENTINEL);
        }
        return current().farmIds();
    }

    public void requireAuthority(String authority) {
        if (!current().authorities().contains(authority)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to do that");
        }
    }

    public void checkFarm(UUID farmId) {
        if (farmId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FARM_REQUIRED", "A farm is required");
        }
        if (!allFarms() && !current().farmIds().contains(farmId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FARM_FORBIDDEN", "You do not have access to this farm");
        }
    }

    public boolean canReadFarm(UUID farmId) {
        return allFarms() || (farmId != null && current().farmIds().contains(farmId));
    }

    public boolean canManageAccess() {
        return current().authorities().contains("ROLE_" + RolePermissions.TENANT_ADMIN);
    }
}

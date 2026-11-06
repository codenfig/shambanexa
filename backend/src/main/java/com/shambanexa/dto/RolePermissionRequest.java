package com.shambanexa.dto;

import java.util.List;

public record RolePermissionRequest(
        List<String> permissions
) {
}

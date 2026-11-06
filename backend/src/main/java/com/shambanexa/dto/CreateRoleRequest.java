package com.shambanexa.dto;

import java.util.List;

public record CreateRoleRequest(
        String label,
        String description,
        List<String> permissions
) {
}

package com.shambanexa.controller;

import com.shambanexa.dto.AccessAssignmentRequest;
import com.shambanexa.dto.AccessCatalogResponse;
import com.shambanexa.dto.CreateRoleRequest;
import com.shambanexa.dto.RoleCreatedResponse;
import com.shambanexa.dto.RolePermissionRequest;
import com.shambanexa.dto.UserAccessView;
import com.shambanexa.implementation.AccessCatalogService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/access")
public class AccessController {

    private final AccessCatalogService catalog;

    public AccessController(AccessCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/roles")
    AccessCatalogResponse roles() {
        return catalog.current();
    }

    @PostMapping("/roles")
    RoleCreatedResponse createRole(@RequestBody CreateRoleRequest request) {
        return catalog.createRole(request);
    }

    @PutMapping("/roles/{code}/permissions")
    void updatePermissions(@PathVariable String code, @RequestBody RolePermissionRequest request) {
        catalog.updatePermissions(code, request);
    }

    @GetMapping("/users")
    List<UserAccessView> users() {
        return catalog.users();
    }

    @PutMapping("/users/{id}/scope")
    UserAccessView assign(@PathVariable UUID id, @Valid @RequestBody AccessAssignmentRequest request) {
        return catalog.assign(id, request);
    }
}

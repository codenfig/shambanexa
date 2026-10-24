package com.shambanexa.controller;

import com.shambanexa.dto.AccessAssignmentRequest;
import com.shambanexa.dto.AccessCatalogResponse;
import com.shambanexa.dto.UserAccessView;
import com.shambanexa.implementation.AccessCatalogService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @GetMapping("/users")
    List<UserAccessView> users() {
        return catalog.users();
    }

    @PutMapping("/users/{id}/scope")
    UserAccessView assign(@PathVariable UUID id, @Valid @RequestBody AccessAssignmentRequest request) {
        return catalog.assign(id, request);
    }
}

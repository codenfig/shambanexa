package com.shambanexa.controller;

import com.shambanexa.dto.LoginRequest;
import com.shambanexa.dto.RegisterRequest;
import com.shambanexa.common.TenantContext;
import com.shambanexa.implementation.OnboardingService;
import com.shambanexa.service.AuthService;
import com.shambanexa.dto.AuthResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final OnboardingService onboarding;

    AuthController(AuthService authService, OnboardingService onboarding) {
        this.authService = authService;
        this.onboarding = onboarding;
    }

    @PostMapping("/login")
    AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password());
    }

    @PostMapping("/register")
    AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return onboarding.register(request);
    }

    @GetMapping("/me")
    AuthResponse me() {
        return authService.profile(TenantContext.require());
    }
}


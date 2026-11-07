package com.shambanexa.controller;

import com.shambanexa.common.TenantContext;
import com.shambanexa.dto.AccountPhoto;
import com.shambanexa.dto.AuthResponse;
import com.shambanexa.dto.PasswordRequest;
import com.shambanexa.dto.PhoneRequest;
import com.shambanexa.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/account")
public class AccountController {

    private final AuthService authService;

    public AccountController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping
    public AuthResponse profile() {
        return authService.profile(TenantContext.require());
    }

    @PutMapping("/phone")
    public AuthResponse phone(@Valid @RequestBody PhoneRequest request) {
        return authService.updatePhone(TenantContext.require(), request.phone());
    }

    @PutMapping("/password")
    public ResponseEntity<Void> password(@Valid @RequestBody PasswordRequest request) {
        authService.changePassword(TenantContext.require(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AuthResponse photo(@RequestParam("file") MultipartFile file) throws IOException {
        return authService.savePhoto(TenantContext.require(), file.getBytes(), file.getContentType());
    }

    @GetMapping("/photo")
    public ResponseEntity<byte[]> photo() {
        AccountPhoto photo = authService.photo(TenantContext.require());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .cacheControl(CacheControl.noStore())
                .body(photo.bytes());
    }
}

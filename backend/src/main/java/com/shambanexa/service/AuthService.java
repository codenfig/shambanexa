package com.shambanexa.service;
import com.shambanexa.dto.AccountPhoto;
import com.shambanexa.dto.AuthResponse;
import com.shambanexa.common.CurrentUser;

import java.util.UUID;

public interface AuthService {
    AuthResponse login(String email, String password);

    CurrentUser load(UUID userId);

    AuthResponse profile(CurrentUser current);

    AuthResponse updatePhone(CurrentUser current, String phone);

    void changePassword(CurrentUser current, String currentPassword, String newPassword);

    AuthResponse savePhoto(CurrentUser current, byte[] bytes, String contentType);

    AccountPhoto photo(CurrentUser current);
}

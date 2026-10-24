package com.shambanexa.service;
import com.shambanexa.dto.AuthResponse;
import com.shambanexa.common.CurrentUser;

import java.util.UUID;

public interface AuthService {
    AuthResponse login(String email, String password);

    CurrentUser load(UUID userId);

    AuthResponse profile(CurrentUser current);
}

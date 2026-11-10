
package com.driveguard.backend.auth.service;

import com.driveguard.backend.auth.dto.request.LoginRequest;
import com.driveguard.backend.auth.dto.response.LoginResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    LoginResponse.UserInfo getCurrentUser(Long userId);
}

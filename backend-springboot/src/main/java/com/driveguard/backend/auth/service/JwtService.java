
package com.driveguard.backend.auth.service;

import org.springframework.security.oauth2.jwt.Jwt;

public interface JwtService {

    String generateToken(Long userId, String role);

    Jwt verifyToken(String token);
}

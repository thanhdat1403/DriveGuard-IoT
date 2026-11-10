
package com.driveguard.backend.auth.controller;

import com.driveguard.backend.auth.dto.request.LoginRequest;
import com.driveguard.backend.auth.dto.response.LoginResponse;
import com.driveguard.backend.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(
            @AuthenticationPrincipal Jwt jwt
    ) {
        if (jwt == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "JWT khong hop le"
            );
        }

        Long userId;

        try {
            userId = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "JWT khong hop le"
            );
        }

        LoginResponse.UserInfo userInfo =
                authService.getCurrentUser(userId);

        return ResponseEntity.ok(
                Map.of(
                        "success", true,
                        "data", userInfo
                )
        );
    }
}


package com.driveguard.backend.admin.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminTestController {

    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> test(
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                Map.of(
                        "success", true,
                        "message", "ADMIN access granted",
                        "userId", authentication.getName(),
                        "role", "ADMIN"
                )
        );
    }
}

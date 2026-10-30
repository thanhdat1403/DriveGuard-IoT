
package com.driveguard.backend.health.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {

        return ResponseEntity.ok(
                Map.of(
                        "success", true,
                        "message", "DriveGuard Spring Boot Backend is running",
                        "timestamp", Instant.now().toString()
                )
        );
    }
}

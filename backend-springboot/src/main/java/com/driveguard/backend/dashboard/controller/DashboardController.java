
package com.driveguard.backend.dashboard.controller;

import com.driveguard.backend.dashboard.dto.response.DashboardSummaryResponse;
import com.driveguard.backend.dashboard.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    // Constructor injection, không phụ thuộc Lombok.
    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    // Lấy dữ liệu thống kê của một thiết bị.
    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryResponse> getSummary(
            @RequestParam Long deviceId
    ) {
        if (deviceId == null || deviceId <= 0) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "deviceId must be a positive number"
            );
        }

        return ResponseEntity.ok(
                dashboardService.getSummary(deviceId)
        );
    }
}

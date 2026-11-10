package com.driveguard.backend.alert.controller;

import com.driveguard.backend.alert.dto.response.AlertResponse;
import com.driveguard.backend.alert.service.AlertQueryService;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AlertQueryService alertQueryService;

    @GetMapping
    public ResponseEntity<Page<AlertResponse>> getAlerts(
            @RequestParam Long deviceId,
            @RequestParam(defaultValue = "false")
            boolean sosOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ){
        Page<AlertResponse> response =
                alertQueryService.getAlerts(
                        deviceId,
                        sosOnly,
                        page,
                        size
                );
        return ResponseEntity.ok(response);
    }
}

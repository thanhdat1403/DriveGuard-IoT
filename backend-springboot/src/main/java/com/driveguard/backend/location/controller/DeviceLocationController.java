package com.driveguard.backend.location.controller;

import com.driveguard.backend.location.dto.response.DeviceLocationResponse;
import com.driveguard.backend.location.service.DeviceLocationService;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/v1/devices/{deviceId}/locations")
@RequiredArgsConstructor
public class DeviceLocationController {

    private final DeviceLocationService locationService;

    // Lấy vị trí mới nhất theo thời điểm GPS đo
    // Lấy vị trí mới nhất theo thời điểm GPS đo.
    @GetMapping("/latest")
    public ResponseEntity<DeviceLocationResponse> getLatestLocation(
            @PathVariable Long deviceId
    ) {
        validateDeviceId(deviceId);

        DeviceLocationResponse response = locationService
                .getLatestLocation(deviceId)
                .map(DeviceLocationResponse::fromEntity)
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND,
                        "No GPS location found for deviceId: " + deviceId
                ));

        return ResponseEntity.ok(response);
    }

    // Lấy lịch sử GPS, có giới hạn số bản ghi mỗi trang.
    @GetMapping
    public ResponseEntity<Page<DeviceLocationResponse>> getLocationHistory(
            @PathVariable Long deviceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        validateDeviceId(deviceId);

        if (page < 0){
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "page must be >= 0"
            );
        }

        if (size < 1 || size > 100) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "size must be between 1 and 100"
            );
        }

        PageRequest pageable = PageRequest.of(page, size);

        Page<DeviceLocationResponse> response = locationService
                .getLocationHistory(deviceId, pageable)
                .map(DeviceLocationResponse::fromEntity);

        return ResponseEntity.ok(response);
    }

    // Không chấp nhận ID thiết bị không hợp lệ
    private void validateDeviceId(Long deviceId){
        if (deviceId == null || deviceId <= 0){
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "deviceId must be a positive number"
            );
        }
    }
}

package com.driveguard.backend.trip.controller;

import com.driveguard.backend.trip.dto.response.TripResponse;
import com.driveguard.backend.trip.dto.request.TripStartRequest;
import com.driveguard.backend.trip.service.TripService;
import com.driveguard.backend.location.dto.response.DeviceLocationResponse;
import com.driveguard.backend.location.service.DeviceLocationService;
import com.driveguard.backend.location.entity.DeviceLocationEntity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/trips")
@RequiredArgsConstructor
@Validated
public class TripController {

    private final TripService tripService;
    private final DeviceLocationService locationService;

    @PostMapping("/start")
    public ResponseEntity<TripResponse> start(
            @Valid @RequestBody TripStartRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tripService.startTrip(request.assignmentId()));
    }

    @PatchMapping("/{tripId}/complete")
    public ResponseEntity<TripResponse> complete(
            @PathVariable @Positive Long tripId) {
        return ResponseEntity.ok(tripService.completeTrip(tripId));
    }

    @PatchMapping("/{tripId}/cancel")
    public ResponseEntity<TripResponse> cancel(
            @PathVariable @Positive Long tripId) {
        return ResponseEntity.ok(tripService.cancelTrip(tripId));
    }

    @GetMapping("/{tripId}")
    public ResponseEntity<TripResponse> detail(
            @PathVariable @Positive Long tripId) {
        return tripService.getTrip(tripId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/devices/{deviceId}/active")
    public ResponseEntity<TripResponse> active(
            @PathVariable @Positive Long deviceId) {
        return tripService.getActiveTrip(deviceId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/devices/{deviceId}/history")
    public Page<TripResponse> deviceHistory(
            @PathVariable @Positive Long deviceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        validatePagination(page, size);
        return tripService.getDeviceTrips(
                deviceId, PageRequest.of(page, size));
    }

    @GetMapping("/drivers/{driverId}/history")
    public Page<TripResponse> driverHistory(
            @PathVariable @Positive Long driverId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        validatePagination(page, size);
        return tripService.getDriverTrips(
                driverId, PageRequest.of(page, size));
    }


    @GetMapping("/{tripId}/locations")
    public ResponseEntity<Page<DeviceLocationResponse>> tripLocations(
            @PathVariable @Positive Long tripId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size
    ) {
        validatePagination(page, size);

        Page<DeviceLocationResponse> locations = locationService
                .getTripLocationHistory(
                        tripId,
                        PageRequest.of(page, size)
                )
                .map(DeviceLocationResponse::fromEntity);

        return ResponseEntity.ok(locations);
    }


    private void validatePagination(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "page phải >= 0 và size phải từ 1 đến 100"
            );
        }
    }
}

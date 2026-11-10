package com.driveguard.backend.driver.controller;

import com.driveguard.backend.driver.dto.request.DriverCreateRequest;
import com.driveguard.backend.driver.dto.response.DriverResponse;
import com.driveguard.backend.driver.service.DriverService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/drivers")
@RequiredArgsConstructor
@Validated // Nhãn kiểm tra tính hợp lệ của dữ liệu
public class DriverController {

    private final DriverService driverService;

    /**
     * POST /api/v1/drivers
     *
     * Tạo tài khoản User với role DRIVER
     * và hồ sơ Driver tương ứng.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DriverResponse> createDriver(
            @Valid @RequestBody DriverCreateRequest request
    ){

        DriverResponse response =
                driverService.createDriver(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);

    }

    /**
     * GET /api/v1/drivers
     *
     * Lấy danh sách tất cả tài xế.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public  ResponseEntity<List<DriverResponse>> getAllDrivers(){

        return ResponseEntity.ok(
                driverService.getAllDrivers()
        );
    }

    /**
     * GET /api/v1/drivers/{driverId}
     *
     * Lấy chi tiết tài xế theo Driver ID.
     */
    @GetMapping("/{driverId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DriverResponse> getDriverById(
            @PathVariable
            @Positive(message = "Driver ID phải là số nguyên dương")
            Long driverId
    ){

        return ResponseEntity.ok(
                driverService.getDriverById(driverId)
        );
    }
}

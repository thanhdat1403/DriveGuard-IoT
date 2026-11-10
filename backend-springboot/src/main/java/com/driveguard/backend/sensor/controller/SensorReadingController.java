package com.driveguard.backend.sensor.controller;

import com.driveguard.backend.sensor.dto.response.SensorReadingResponse;
import com.driveguard.backend.sensor.service.SensorReadingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller cung cấp API truy vấn dữ liệu cảm biến
 * cho Frontend React Dashboard của hệ thống DriveGuard IoT.
 *
 * Nhiệm vụ:
 * - Tiếp nhận HTTP Request từ Frontend.
 * - Kiểm tra các tham số đầu vào cơ bản.
 * - Gọi tầng SensorReadingService xử lý nghiệp vụ.
 * - Trả dữ liệu dưới dạng JSON thông qua ResponseEntity.
 *
 * Controller không truy cập trực tiếp Repository.
 * Controller không thực hiện truy vấn SQL.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/sensor-readings")
public class SensorReadingController {

    // Interface tầng Service xử lý nghiệp vụ dữ liệu cảm biến.
    // Spring tự động inject SensorReadingServiceImpl.
    private final SensorReadingService sensorReadingService;

    /**
     * API lấy dữ liệu cảm biến mới nhất của một thiết bị.
     *
     * HTTP Method: GET
     *
     * Endpoint:
     * /api/v1/sensor-readings/devices/{deviceId}/latest
     *
     * Ví dụ:
     * GET /api/v1/sensor-readings/devices/1/latest
     *
     * @PathVariable lấy deviceId trực tiếp từ URL.
     *
     * @return HTTP 200 cùng SensorReadingResponse khi thành công.
     */
    @GetMapping("/devices/{deviceId}/latest")
    public ResponseEntity<SensorReadingResponse> getLatestReading(
            @PathVariable Long deviceId
    ){
        // Ghi log yêu cầu lấy dữ liệu cảm biến mới nhất
        log.info(
                "[SENSOR API] Yeu cau lay du lieu moi nhat | Device ID: {}",
                deviceId
        );

        // Gọi tầng Service để truy vấn và chuyển Entity thành DTO
        SensorReadingResponse response =
                sensorReadingService.getLatestReading(deviceId);

        // Trả HTTP 200 OK và dữ liệu JSON cho Frontend
        return ResponseEntity.ok(response);

    }

    /**
     * API lấy lịch sử dữ liệu cảm biến theo thiết bị.
     *
     * HTTP Method: GET
     *
     * Endpoint:
     * /api/v1/sensor-readings/devices/{deviceId}
     *
     * Ví dụ:
     * GET /api/v1/sensor-readings/devices/1?page=0&size=20
     *
     * page bắt đầu từ 0.
     * size là số lượng bản ghi trên một trang.
     *
     * @return HTTP 200 cùng dữ liệu phân trang.
     */
    @GetMapping("/devices/{deviceId}")
    public ResponseEntity<Page<SensorReadingResponse>> getReadingHistory(
            @PathVariable Long deviceId,

            @RequestParam(defaultValue = "0") int page,

            @RequestParam(defaultValue = "20") int size
    ){
        // Kiểm tra số trang không được âm
        if (page < 0){
            throw new IllegalArgumentException(
                    "So trang phai lon hon hoac bang 0"
            );
        }

        // Giới hạn kích thước trang để tránh truy vấn quá nhiều dữ liệu
        if (size < 1 || size > 100){
            throw new IllegalArgumentException(
                    "Kich thuoc trang phai nam trong khoang 1 den 100"
            );
        }

        // Ghi log yêu cầu truy vấn lịch sử cảm biến
        log.info(
                "[SENSOR API] Lay lich su | Device ID: {} | Page: {} | Size: {}",
                deviceId,
                page,
                size
        );

        // Tạo đối tượng phân trang.
        // Repository đã định nghĩa sắp xếp theo measuredAt DESC, id DESC.
        PageRequest pageable = PageRequest.of(page, size);

        // Gọi tầng Service lấy dữ liệu cảm biến từ MariaDB
        Page<SensorReadingResponse> response =
                sensorReadingService.getReadingHistory(
                        deviceId,
                        pageable
                );

        // Trả HTTP 200 OK và dữ liệu lịch sử dạng JSON
        return ResponseEntity.ok(response);

    }
}

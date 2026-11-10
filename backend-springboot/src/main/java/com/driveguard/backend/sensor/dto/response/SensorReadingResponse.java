
package com.driveguard.backend.sensor.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO dùng để trả dữ liệu cảm biến từ Backend Spring Boot
 * về Frontend React thông qua REST API.
 *
 * DTO không phải Entity và không ánh xạ trực tiếp
 * với bảng sensor_readings trong MariaDB.
 *
 * Mục đích:
 * - Không trả trực tiếp JPA Entity ra Frontend.
 * - Kiểm soát các trường dữ liệu được phép trả về.
 * - Tránh các vấn đề liên quan đến quan hệ JPA và Lazy Loading.
 * - Chuẩn bị dữ liệu JSON để hiển thị trên React Dashboard.
 *
 * Quy ước thời gian:
 * - measuredAt, receivedAt và createdAt được lưu theo UTC.
 * - Frontend sẽ chuyển sang múi giờ Việt Nam khi hiển thị.
 */
@Getter
@Builder
public class SensorReadingResponse {

    // ID của bản ghi dữ liệu cảm biến
    private Long id;

    // ID của thiết bị gửi dữ liệu
    private Long deviceId;

    // Mã thiết bị ESP32-S3 đã đăng ký trong hệ thống
    private String deviceCode;

    // Tên thiết bị để hiển thị trên Dashboard
    private String deviceName;

    // Thời điểm đo dữ liệu cảm biến theo UTC
    private LocalDateTime measuredAt;

    // Thời điểm Backend nhận dữ liệu theo UTC
    private LocalDateTime receivedAt;

    // Nhịp tim của tài xế (BPM)
    private BigDecimal heartRate;

    // Nồng độ oxy trong máu (%)
    private BigDecimal spo2;

    // Trạng thái AI tại thiết bị
    private String edgeAiStatus;

    // Độ tin cậy AI từ 0 đến 1
    private BigDecimal edgeAiConfidence;

    // Trạng thái phân tích từ Backend
    private String serverStatus;

    // Mức độ nguy hiểm được hệ thống đánh giá
    private String riskLevel;

    // Kết quả phát hiện ngủ gật
    private Boolean isDrowsy;

    // Thời điểm tạo bản ghi trong MariaDB theo UTC
    private LocalDateTime createdAt;
}

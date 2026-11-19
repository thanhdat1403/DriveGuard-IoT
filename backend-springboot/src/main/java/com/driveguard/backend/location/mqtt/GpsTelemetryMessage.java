package com.driveguard.backend.location.mqtt;

// Dùng để lưu số thập phân chính xác tuyệt đối, tránh sai số làm tròn khi xử lý tọa độ GPS
import java.math.BigDecimal;
// Dùng để lưu trữ thông tin ngày và giờ thiết bị ghi nhận dữ liệu
import java.time.LocalDateTime;

/**
 * Chứa dữ liệu GPS đã được phân tích từ bản tin MQTT.
 *
 * Record tự tạo constructor, các phương thức truy cập dữ liệu,
 * equals(), hashCode() và toString().
 *
 * measuredAt được quy ước là thời gian UTC.
 * LocalDateTime không tự lưu thông tin múi giờ.
 */
public record GpsTelemetryMessage(
        String deviceCode,        // Mã định danh thiết bị
        String messageId,         // Mã chống xử lý trùng bản tin GPS
        BigDecimal latitude,      // Vĩ độ (-90 đến 90)
        BigDecimal longitude,     // Kinh độ (-180 đến 180)
        BigDecimal accuracyM,     // Sai số vị trí (mét)
        BigDecimal speedKmh,      // Vận tốc (km/h)
        BigDecimal headingDeg,    // Hướng di chuyển [0, 360) độ
        LocalDateTime measuredAt  // Thời điểm đo, quy ước UTC
) {
}

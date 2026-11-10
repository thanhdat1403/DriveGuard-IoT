
package com.driveguard.backend.sensor.enums;

/**
 * Mức độ rủi ro được xác định bởi hệ thống DriveGuard IoT.
 *
 * LOW:
 *   Mức rủi ro thấp.
 *
 * MEDIUM:
 *   Mức rủi ro trung bình.
 *
 * HIGH:
 *   Mức rủi ro cao.
 *
 * CRITICAL:
 *   Mức rủi ro nghiêm trọng.
 *
 * Lưu ý:
 *   RiskLevel chỉ biểu diễn mức độ ưu tiên cảnh báo
 *   theo quy tắc đánh giá của hệ thống.
 *
 *   Không phải kết luận chẩn đoán y khoa.
 */
public enum RiskLevel {

    LOW,

    MEDIUM,

    HIGH,

    CRITICAL
}

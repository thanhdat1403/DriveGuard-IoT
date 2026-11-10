
package com.driveguard.backend.sensor.enums;

/**
 * Trạng thái đánh giá dữ liệu cảm biến phía Server.
 *
 * Đây là kết quả phân tích của Backend,
 * không phải trạng thái Edge AI do ESP32-S3 gửi lên.
 *
 * Các trạng thái:
 *
 * NORMAL:
 *   Chưa phát hiện dấu hiệu bất thường theo quy tắc.
 *
 * WARNING:
 *   Phát hiện dấu hiệu cần chú ý.
 *
 * DANGER:
 *   Phát hiện dấu hiệu nguy hiểm theo quy tắc cảnh báo.
 *
 * CRITICAL:
 *   Cảnh báo nghiêm trọng, cần ưu tiên xử lý.
 *
 * UNKNOWN:
 *   Không đủ dữ liệu hoặc dữ liệu không hợp lệ
 *   để đưa ra đánh giá.
 *
 * Lưu ý:
 *   Các trạng thái này chỉ dùng để cảnh báo hỗ trợ.
 *   Không được xem là kết luận chẩn đoán đột quỵ.
 */
public enum ServerStatus {

    NORMAL,

    WARNING,

    DANGER,

    CRITICAL,

    UNKNOWN
}

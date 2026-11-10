
package com.driveguard.backend.sensor.dto.result;

import com.driveguard.backend.sensor.enums.RiskLevel;
import com.driveguard.backend.sensor.enums.ServerStatus;

import lombok.Builder;
import lombok.Getter;

/**
 * DTO chứa kết quả đánh giá rủi ro phía Server.
 *
 * Được tạo bởi RiskAssessmentServiceImpl sau khi
 * phân tích dữ liệu cảm biến và trạng thái Edge AI.
 *
 * DTO này:
 * - Không phải JPA Entity.
 * - Không ánh xạ trực tiếp vào database.
 * - Không chứa logic xử lý nghiệp vụ.
 * - Chỉ dùng để truyền kết quả đánh giá giữa các Service.
 *
 * Kết quả đánh giá không phải chẩn đoán đột quỵ.
 */
@Getter
@Builder
public class RiskAssessmentResult {

    /**
     * Trạng thái đánh giá phía Server.
     *
     * Ví dụ:
     * NORMAL, WARNING, DANGER, CRITICAL, UNKNOWN.
     */
    private final ServerStatus serverStatus;

    /**
     * Mức độ rủi ro tương ứng.
     *
     * Ví dụ:
     * LOW, MEDIUM, HIGH, CRITICAL.
     *
     * Có thể null khi serverStatus = UNKNOWN,
     * vì hệ thống chưa đủ dữ liệu để đánh giá.
     */
    private final RiskLevel riskLevel;
}

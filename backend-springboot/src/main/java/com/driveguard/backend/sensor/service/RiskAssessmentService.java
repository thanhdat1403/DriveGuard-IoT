
package com.driveguard.backend.sensor.service;

import com.driveguard.backend.sensor.dto.result.RiskAssessmentResult;

import java.math.BigDecimal;

/**
 * Service Interface đánh giá mức độ rủi ro
 * từ dữ liệu cảm biến và kết quả Edge AI.
 *
 * Nhiệm vụ:
 * - Tiếp nhận dữ liệu nhịp tim và SpO2.
 * - Tiếp nhận trạng thái AI từ ESP32-S3.
 * - Tiếp nhận độ tin cậy của Edge AI.
 * - Tiếp nhận trạng thái phát hiện ngủ gật.
 * - Chuyển dữ liệu cho tầng xử lý nghiệp vụ đánh giá rủi ro.
 *
 * Nguyên tắc thiết kế:
 * - Interface chỉ khai báo hợp đồng nghiệp vụ.
 * - Không chứa logic đánh giá rủi ro.
 * - Không truy cập trực tiếp Repository.
 * - Không phụ thuộc vào MQTT Controller.
 *
 * Lưu ý:
 * - Kết quả đánh giá chỉ phục vụ cảnh báo hỗ trợ.
 * - Không phải kết luận chẩn đoán đột quỵ.
 */
public interface RiskAssessmentService {

    /**
     * Đánh giá dữ liệu cảm biến và kết quả Edge AI.
     *
     * @param heartRate         Nhịp tim (BPM).
     * @param spo2              Nồng độ oxy trong máu (%).
     * @param edgeAiStatus      Trạng thái AI từ thiết bị.
     * @param edgeAiConfidence  Độ tin cậy AI (0.0 - 1.0).
     * @param isDrowsy          Trạng thái phát hiện ngủ gật.
     *
     * @return Kết quả đánh giá rủi ro phía Server.
     */
    RiskAssessmentResult assess(
            BigDecimal heartRate,
            BigDecimal spo2,
            String edgeAiStatus,
            BigDecimal edgeAiConfidence,
            Boolean isDrowsy
    );
}


package com.driveguard.backend.sensor.service;

import com.driveguard.backend.sensor.entity.SensorReadingEntity;
import com.driveguard.backend.sensor.dto.response.SensorReadingResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Interface định nghĩa các nghiệp vụ xử lý dữ liệu cảm biến.
 *
 * Tầng MQTT hoặc Controller chỉ cần gọi Interface này,
 * không phụ thuộc trực tiếp vào Class triển khai.
 */
public interface SensorReadingService {

    /**
     * Lưu dữ liệu cảm biến nhận được từ ESP32-S3.
     *
     * @param deviceCode Mã thiết bị đã đăng ký trong database.
     * @param measuredAt Thời điểm đo theo chuẩn UTC.
     * @param heartRate Nhịp tim (BPM).
     * @param spo2 Nồng độ oxy trong máu (%).
     * @param edgeAiStatus Trạng thái phân tích AI tại thiết bị.
     * @param edgeAiConfidence Độ tin cậy AI từ 0 đến 1.
     * @param isDrowsy Kết quả phát hiện ngủ gật.
     * @param rawPayload Chuỗi JSON gốc nhận từ MQTT.
     * @return Bản ghi cảm biến đã lưu.
     */
    SensorReadingEntity saveReading(
            String deviceCode,
            LocalDateTime measuredAt,
            BigDecimal heartRate,
            BigDecimal spo2,
            String edgeAiStatus,
            BigDecimal edgeAiConfidence,
            Boolean isDrowsy,
            String rawPayload
    );


    /**
     * Lấy bản ghi dữ liệu cảm biến mới nhất của một thiết bị.
     *
     * @param deviceId ID của thiết bị trong bảng devices.
     * @return DTO chứa dữ liệu cảm biến mới nhất.
     *
     * Nếu thiết bị không tồn tại hoặc chưa có dữ liệu,
     * Service sẽ thông báo lỗi tương ứng.
     */
    SensorReadingResponse getLatestReading(Long deviceId);

    /**
     * Lấy lịch sử dữ liệu cảm biến của một thiết bị.
     *
     * @param deviceId ID của thiết bị trong bảng devices.
     * @param pageable Thông tin phân trang.
     * @return Page chứa danh sách SensorReadingResponse.
     *
     * Dữ liệu được sắp xếp theo thời gian đo giảm dần,
     * bản ghi mới nhất được hiển thị trước.
     */
    Page<SensorReadingResponse> getReadingHistory(
            Long deviceId,
            Pageable pageable
    );

}

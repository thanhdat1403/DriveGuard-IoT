
package com.driveguard.backend.sensor.entity;

import com.driveguard.backend.device.entity.DeviceEntity;
import com.driveguard.backend.sensor.enums.RiskLevel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity ánh xạ bảng sensor_readings trong MariaDB.
 *
 * Mỗi bản ghi đại diện cho một lần hệ thống
 * tiếp nhận dữ liệu cảm biến từ thiết bị ESP32-S3.
 *
 * Thời gian được lưu theo chuẩn UTC.
 */
@Getter
@Setter
@Entity
@Table(name = "sensor_readings")
public class SensorReadingEntity {

    // Khóa chính của bảng sensor_readings
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Thiết bị gửi dữ liệu cảm biến
    // Khóa ngoại device_id tham chiếu đến bảng devices
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private DeviceEntity device;

    // Thời điểm dữ liệu được đo tại thiết bị (UTC)
    @Column(name = "measured_at", nullable = false)
    private LocalDateTime measuredAt;

    // Thời điểm backend nhận dữ liệu (UTC)
    // Giá trị mặc định được MariaDB tự động tạo
    @Column(
            name = "received_at",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private LocalDateTime receivedAt;

    // Nhịp tim của tài xế (BPM)
    @Column(name = "heart_rate", precision = 6, scale = 2)
    private BigDecimal heartRate;

    // Nồng độ oxy trong máu (%)
    @Column(name = "spo2", precision = 5, scale = 2)
    private BigDecimal spo2;

    // Trạng thái phân tích AI tại thiết bị
    @Column(name = "edge_ai_status", length = 50)
    private String edgeAiStatus;

    // Độ tin cậy của mô hình AI, từ 0 đến 1
    @Column(name = "edge_ai_confidence", precision = 5, scale = 4)
    private BigDecimal edgeAiConfidence;

    // Trạng thái do backend đánh giá
    @Column(name = "server_status", length = 50)
    private String serverStatus;

    // Mức độ nguy hiểm được đánh giá
    // Enum ánh xạ chính xác các giá trị risk_level trong MariaDB
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 20)
    private RiskLevel riskLevel;

    // Kết quả phát hiện ngủ gật
    @Column(name = "is_drowsy")
    private Boolean isDrowsy;

    // Dữ liệu JSON gốc nhận từ MQTT
    @Column(name = "raw_payload", columnDefinition = "LONGTEXT")
    private String rawPayload;

    // Thời điểm bản ghi được tạo trong database (UTC)
    @Column(
            name = "created_at",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

}

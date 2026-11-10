package com.driveguard.backend.deviceevent.dto.mqtt;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO biểu diễn một sự kiện do ESP32-S3 gửi lên MQTT.
 *
 * Chỉ chứa dữ liệu đầu vào từ thiết bị.
 * Không chứa ID database hoặc trạng thái xử lý cảnh báo.
 *
 * timestampMs là thời gian tính từ khi ESP32 khởi động,
 * không phải Unix timestamp.
 */
@Getter
@Setter
@NoArgsConstructor
public class DeviceEventMessage {

    /**
     * Mã duy nhất của sự kiện.
     *
     * Dự kiến tạo từ bootId + sequence để tránh trùng
     * sau khi ESP32-S3 khởi động lại.
     */
    private String eventId;

    /**
     * Mã thiết bị đã đăng ký trong bảng devices.
     * Ví dụ: esp32s3-demo
     */
    private String deviceId;

    /**
     * Loại sự kiện.
     *
     * NO_RESPONSE_SOS
     * MANUAL_SOS
     * SOS_ACKNOWLEDGED
     * SOS_SOUND_MUTED
     * DROWSINESS_ACKNOWLEDGED
     */
    private String eventType;

    /**
     * Thời gian từ lúc ESP32 khởi động, đơn vị ms.
     *
     * Chưa phải thời gian thực UTC.
     */
    private Long timestampMs;

    /**
     * ID của lần khởi động thiết bị.
     * Dùng kết hợp sequence để tạo eventId duy nhất.
     */
    private String bootId;

    /**
     * Dinh danh mot dot SOS.
     * Giu nguyen cho tat ca su kien trong cung dot.
     * Tam thoi nullable de tuong thich firmware cu.
     */
    private String sosEpisodeId;

    /**
     * Số thứ tự sự kiện trong một lần khởi động.
     */
    private Long sequence;
}
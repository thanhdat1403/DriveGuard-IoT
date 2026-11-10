package com.driveguard.backend.deviceevent.service;

import com.driveguard.backend.deviceevent.dto.mqtt.DeviceEventMessage;
import com.driveguard.backend.deviceevent.entity.DeviceEventEntity;

public interface DeviceEventService {

    /**
     * Tiếp nhận và lưu một sự kiện từ MQTT.
     *
     * @param message Dữ liệu sự kiện do ESP32-S3 gửi lên
     * @param rawPayload JSON gốc nhận từ MQTT
     *
     * @return Entity đã lưu hoặc đã tồn tại.
     *
     * Việc xác thực dữ liệu, chống trùng eventId,
     * tìm thiết bị và quản lý transaction
     * sẽ được triển khai tại DeviceEventServiceImpl.
     */
    DeviceEventEntity saveEvent(
            DeviceEventMessage message,
            String rawPayload
    );
}

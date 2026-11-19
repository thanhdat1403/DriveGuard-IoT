
package com.driveguard.backend.deviceevent.service;

import com.driveguard.backend.deviceevent.dto.response.DeviceEventResponse;
import org.springframework.data.domain.Page;

public interface DeviceEventQueryService {

    // Lấy lịch sử sự kiện theo thiết bị, có thể lọc theo loại.
    Page<DeviceEventResponse> getDeviceEvents(
            Long deviceId,
            String eventType,
            int page,
            int size
    );

    // Lấy chi tiết một sự kiện theo mã MQTT.
    DeviceEventResponse getEventByEventId(String eventId);

    // Lấy lịch sử sự kiện thuộc một đợt SOS.
    Page<DeviceEventResponse> getSosEpisodeEvents(
            Long deviceId,
            String sosEpisodeId,
            int page,
            int size
    );
}

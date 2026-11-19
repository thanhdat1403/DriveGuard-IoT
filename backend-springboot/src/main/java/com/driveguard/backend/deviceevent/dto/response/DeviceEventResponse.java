
package com.driveguard.backend.deviceevent.dto.response;

import com.driveguard.backend.deviceevent.entity.DeviceEventEntity;

import java.time.LocalDateTime;

/**
 * DTO trả dữ liệu sự kiện thiết bị cho Flutter và Web Dashboard.
 * Không trả rawPayload để tránh lộ dữ liệu gốc và giảm dung lượng JSON.
 */
public record DeviceEventResponse(
        Long id,
        String eventId,
        Long deviceId,
        String eventType,
        String sosEpisodeId,
        Long deviceUptimeMs,
        LocalDateTime occurredAt,
        LocalDateTime receivedAt,
        LocalDateTime createdAt
) {

    // Chuyển dữ liệu từ Entity sang DTO trả về API.
    public static DeviceEventResponse fromEntity(DeviceEventEntity event) {
        return new DeviceEventResponse(
                event.getId(),
                event.getEventId(),
                event.getDeviceId(),
                event.getEventType(),
                event.getSosEpisodeId(),
                event.getDeviceUptimeMs(),
                event.getOccurredAt(),
                event.getReceivedAt(),
                event.getCreatedAt()
        );
    }
}


package com.driveguard.backend.deviceassignment.dto.response;

import com.driveguard.backend.deviceassignment.entity.DeviceAssignmentEntity;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Getter
@Builder
public class DeviceAssignmentResponse {

    private Long id;

    private Long deviceId;
    private Long driverId;

    private OffsetDateTime startedAt;
    private OffsetDateTime endedAt;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    /**
     * Chuyển Entity thành Response.
     *
     * Quy ước:
     * LocalDateTime trong database biểu diễn UTC.
     * Response trả OffsetDateTime với offset UTC.
     */
    public static DeviceAssignmentResponse fromEntity(
            DeviceAssignmentEntity entity
    ) {
        return DeviceAssignmentResponse.builder()
                .id(entity.getId())
                .deviceId(entity.getDevice().getId())
                .driverId(entity.getDriver().getId())
                .startedAt(toUtc(entity.getStartedAt()))
                .endedAt(toUtc(entity.getEndedAt()))
                .createdAt(toUtc(entity.getCreatedAt()))
                .updatedAt(toUtc(entity.getUpdatedAt()))
                .build();
    }

    private static OffsetDateTime toUtc(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }

        return dateTime.atOffset(ZoneOffset.UTC);
    }
}

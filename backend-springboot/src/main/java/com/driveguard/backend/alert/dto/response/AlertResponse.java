
package com.driveguard.backend.alert.dto.response;

import com.driveguard.backend.alert.entity.AlertEntity;
import com.driveguard.backend.alert.enums.AlertEpisodeStatus;
import com.driveguard.backend.alert.enums.AlertSeverity;
import com.driveguard.backend.alert.enums.AlertStatus;

import java.time.LocalDateTime;

public record AlertResponse(
        Long id,
        Long deviceId,
        Long driverId,
        Long readingId,
        String alertType,
        String sosEpisodeId,
        AlertSeverity severity,
        AlertStatus status,
        AlertEpisodeStatus episodeStatus,
        String description,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        Long acknowledgedBy,
        LocalDateTime acknowledgedAt,
        Long resolvedBy,
        LocalDateTime resolvedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AlertResponse fromEntity(AlertEntity a) {
        return new AlertResponse(
                a.getId(),
                a.getDeviceId(),
                a.getDriverId(),
                a.getReadingId(),
                a.getAlertType(),
                a.getSosEpisodeId(),
                a.getSeverity(),
                a.getStatus(),
                a.getEpisodeStatus(),
                a.getDescription(),
                a.getStartedAt(),
                a.getEndedAt(),
                a.getAcknowledgedBy(),
                a.getAcknowledgedAt(),
                a.getResolvedBy(),
                a.getResolvedAt(),
                a.getCreatedAt(),
                a.getUpdatedAt()
        );
    }
}

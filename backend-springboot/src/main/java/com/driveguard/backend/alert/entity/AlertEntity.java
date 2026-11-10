
package com.driveguard.backend.alert.entity;

import com.driveguard.backend.alert.enums.AlertEpisodeStatus;
import com.driveguard.backend.alert.enums.AlertSeverity;
import com.driveguard.backend.alert.enums.AlertStatus;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "alerts")
@Getter
@Setter
@NoArgsConstructor
public class AlertEntity {

    // ==================== ID ====================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    // ==================== RELATIONSHIPS ====================

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(name = "driver_id")
    private Long driverId;

    @Column(name = "reading_id")
    private Long readingId;

    // ==================== ALERT INFORMATION ====================

    @Column(name = "alert_type", nullable = false, length = 50)
    private String alertType;

    @Column(
            name = "sos_episode_id",
            length = 150
    )
    private String sosEpisodeId;


    @Enumerated(EnumType.STRING)
    @Column(
            name = "severity",
            nullable = false,
            columnDefinition = "ENUM('LOW','MEDIUM','HIGH','CRITICAL')"
    )
    private AlertSeverity severity = AlertSeverity.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            columnDefinition =
                    "ENUM('NEW','ACKNOWLEDGED','RESOLVED','FALSE_ALARM')"
    )
    private AlertStatus status = AlertStatus.NEW;

    // ==================== EPISODE ====================

    @Enumerated(EnumType.STRING)
    @Column(
            name = "episode_status",
            nullable = false,
            columnDefinition = "ENUM('ACTIVE','ENDED')"
    )
    private AlertEpisodeStatus episodeStatus =
            AlertEpisodeStatus.ACTIVE;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    // ==================== ACKNOWLEDGEMENT ====================

    @Column(name = "acknowledged_by")
    private Long acknowledgedBy;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    // ==================== RESOLUTION ====================

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    // ==================== AUDIT ====================

    @Column(
            name = "created_at",
            insertable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    @Column(
            name = "updated_at",
            insertable = false,
            updatable = false
    )
    private LocalDateTime updatedAt;

    // ==================== LIFECYCLE ====================

    @PrePersist
    protected void onCreate() {

        if (startedAt == null) {
            startedAt = LocalDateTime.now(ZoneOffset.UTC);
        }

        if (severity == null) {
            severity = AlertSeverity.MEDIUM;
        }

        if (status == null) {
            status = AlertStatus.NEW;
        }

        if (episodeStatus == null) {
            episodeStatus = AlertEpisodeStatus.ACTIVE;
        }
    }
}

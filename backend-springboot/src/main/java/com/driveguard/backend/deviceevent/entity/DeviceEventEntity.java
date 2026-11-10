
package com.driveguard.backend.deviceevent.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "device_events",
        indexes = {
                @Index(
                        name = "idx_device_events_device_received",
                        columnList = "device_id, received_at"
                ),
                @Index(
                        name = "idx_device_events_type_received",
                        columnList = "event_type, received_at"
                )
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_device_events_event_id",
                        columnNames = "event_id"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class DeviceEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @Column(
            name = "event_id",
            nullable = false,
            length = 150
    )
    private String eventId;

    @Column(
            name = "device_id",
            nullable = false,
            columnDefinition = "BIGINT UNSIGNED"
    )
    private Long deviceId;

    @Column(
            name = "event_type",
            nullable = false,
            length = 50
    )
    private String eventType;

    @Column(
            name = "sos_episode_id",
            length = 150
    )
    private String sosEpisodeId;


    @Column(
            name = "device_uptime_ms",
            columnDefinition = "BIGINT UNSIGNED"
    )
    private Long deviceUptimeMs;

    @Column(name = "occurred_at")
    private LocalDateTime occurredAt;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Lob
    @Column(
            name = "raw_payload",
            nullable = false,
            columnDefinition = "LONGTEXT"
    )
    private String rawPayload;

    @Column(
            name = "created_at",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private LocalDateTime createdAt;
}

package com.driveguard.backend.mqtt.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "mqtt_dead_letters",
        indexes = {
                @Index(
                        name = "idx_mqtt_dlq_received",
                        columnList = "received_at"
                ),
                @Index(
                        name = "idx_mqtt_dlq_error_received",
                        columnList = "error_code, received_at"
                ),
                @Index(
                        name = "idx_mqtt_dlq_payload_hash",
                        columnList = "payload_hash"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class MqttDeadLetterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(
            name = "id",
            columnDefinition = "BIGINT UNSIGNED"
    )
    private Long id;

    @Column(
            name = "source_topic",
            nullable = false,
            length = 255
    )
    private String sourceTopic;

    @Column(
            name = "payload_hash",
            nullable = false,
            length = 64
    )
    private String payloadHash;

    @Lob
    @Column(
            name = "raw_payload",
            nullable = false,
            columnDefinition = "LONGBLOB"
    )
    private byte[] rawPayload;

    @Column(
            name = "error_code",
            nullable = false,
            length = 80
    )
    private String errorCode;

    @Column(
            name = "error_message",
            length = 500
    )
    private String errorMessage;

    @Column(
            name = "received_at",
            nullable = false
    )
    private LocalDateTime receivedAt;

    @Column(
            name = "created_at",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private LocalDateTime createdAt;
}
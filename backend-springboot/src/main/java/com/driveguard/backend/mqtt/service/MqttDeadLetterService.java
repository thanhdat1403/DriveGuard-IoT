package com.driveguard.backend.mqtt.service;

import com.driveguard.backend.mqtt.entity.MqttDeadLetterEntity;
import com.driveguard.backend.mqtt.repository.MqttDeadLetterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class MqttDeadLetterService {

    private final MqttDeadLetterRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long saveInvalidMessage(
            String topic,
            byte[] payload,
            String errorCode,
            String errorMessage
    ) {
        if (topic == null || topic.isBlank()
                || payload == null
                || errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("Invalid DLQ arguments");
        }

        if (topic.length() > 255 || errorCode.length() > 80) {
            throw new IllegalArgumentException("DLQ metadata too long");
        }

        MqttDeadLetterEntity entity = new MqttDeadLetterEntity();
        entity.setSourceTopic(topic);
        entity.setRawPayload(payload.clone());
        entity.setPayloadHash(sha256(payload));
        entity.setErrorCode(errorCode);
        entity.setErrorMessage(
                errorMessage == null
                        ? null
                        : errorMessage.substring(
                                0,
                                Math.min(errorMessage.length(), 500)
                        )
        );
        entity.setReceivedAt(LocalDateTime.now());

        return repository.saveAndFlush(entity).getId();
    }

    private String sha256(byte[] payload) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(payload);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
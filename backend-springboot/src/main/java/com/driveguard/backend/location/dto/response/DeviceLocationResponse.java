package com.driveguard.backend.location.dto.response;

import com.driveguard.backend.location.entity.DeviceLocationEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// DTO trả về dữ liệu GPS, không để lộ JPA Entity.
public record DeviceLocationResponse(
        Long id,
        Long deviceId,
        String messageId,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal accuracyM,
        BigDecimal speedKmh,
        BigDecimal headingDeg,
        LocalDateTime measuredAt,
        LocalDateTime receivedAt
) {

    // Chuyển Entity sang dữ liệu an toàn cho REST API.
    public static DeviceLocationResponse fromEntity(
            DeviceLocationEntity entity
    ) {
        return new DeviceLocationResponse(
                entity.getId(),
                entity.getDevice().getId(),
                entity.getMessageId(),
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getAccuracyM(),
                entity.getSpeedKmh(),
                entity.getHeadingDeg(),
                entity.getMeasuredAt(),
                entity.getReceivedAt()
        );
    }
}
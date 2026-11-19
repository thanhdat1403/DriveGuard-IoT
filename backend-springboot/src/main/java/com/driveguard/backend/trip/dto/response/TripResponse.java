package com.driveguard.backend.trip.dto.response;

import com.driveguard.backend.trip.entity.TripEntity;
import com.driveguard.backend.trip.enums.TripStatus;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record TripResponse(
        Long id,
        Long assignmentId,
        Long deviceId,
        Long driverId,
        TripStatus status,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt
) {
    // Chuyển thời gian UTC trong database sang JSON có hậu tố Z.
    public static TripResponse fromEntity(TripEntity trip) {
        return new TripResponse(
                trip.getId(),
                trip.getAssignment().getId(),
                trip.getDevice().getId(),
                trip.getDriver().getId(),
                trip.getStatus(),
                toUtc(trip.getStartedAt()),
                toUtc(trip.getEndedAt())
        );
    }

    private static OffsetDateTime toUtc(LocalDateTime value) {
        return value == null
                ? null
                : value.atOffset(ZoneOffset.UTC);
    }
}
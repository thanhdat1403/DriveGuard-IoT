package com.driveguard.backend.trip.dto.request;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TripStartRequest(
        @NotNull(message = "assignmentId không được để trống")
        @Positive(message = "assignmentId phải là số nguyên dương")
        Long assignmentId
) {
}
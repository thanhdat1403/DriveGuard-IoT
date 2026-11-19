
package com.driveguard.backend.trip.service;

import com.driveguard.backend.trip.dto.response.TripResponse;
import com.driveguard.backend.trip.enums.TripStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface TripService {

    TripResponse startTrip(Long assignmentId);

    TripResponse completeTrip(Long tripId);

    TripResponse cancelTrip(Long tripId);

    Optional<TripResponse> getTrip(Long tripId);

    Optional<TripResponse> getActiveTrip(Long deviceId);

    Page<TripResponse> getDeviceTrips(Long deviceId, Pageable pageable);

    Page<TripResponse> getDriverTrips(Long driverId, Pageable pageable);
}

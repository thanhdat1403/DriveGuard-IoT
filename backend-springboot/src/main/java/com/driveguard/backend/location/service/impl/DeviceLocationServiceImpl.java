package com.driveguard.backend.location.service.impl;

import com.driveguard.backend.device.entity.DeviceEntity;
import com.driveguard.backend.device.repository.DeviceRepository;
import com.driveguard.backend.location.entity.DeviceLocationEntity;
import com.driveguard.backend.location.repository.DeviceLocationRepository;
import com.driveguard.backend.location.service.DeviceLocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.driveguard.backend.trip.entity.TripEntity;
import com.driveguard.backend.trip.repository.TripRepository;
import org.springframework.data.domain.PageRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DeviceLocationServiceImpl
        implements DeviceLocationService {

    private final DeviceRepository deviceRepository;
    private final DeviceLocationRepository locationRepository;
    private final TripRepository tripRepository;

    @Override
    @Transactional
    public DeviceLocationEntity saveLocation(
            String deviceCode,
            String messageId,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal accuracyM,
            BigDecimal speedKmh,
            BigDecimal headingDeg,
            LocalDateTime measuredAt
    ) {

        if (deviceCode == null || deviceCode.isBlank()) {
            throw new IllegalArgumentException("deviceCode is required");
        }

        if (messageId == null || messageId.isBlank()
                || messageId.length() > 100) {
            throw new IllegalArgumentException("Invalid GPS messageId");
        }

        if (latitude == null || longitude == null) {
            throw new IllegalArgumentException("GPS coordinates are required");
        }

        if (latitude.compareTo(BigDecimal.valueOf(-90)) < 0
                || latitude.compareTo(BigDecimal.valueOf(90)) > 0) {
            throw new IllegalArgumentException("Invalid latitude");
        }

        if (longitude.compareTo(BigDecimal.valueOf(-180)) < 0
                || longitude.compareTo(BigDecimal.valueOf(180)) > 0) {
            throw new IllegalArgumentException("Invalid longitude");
        }

        if (latitude.scale() > 7 || longitude.scale() > 7) {
            throw new IllegalArgumentException(
                    "GPS coordinate precision exceeds 7 decimal places"
            );
        }

        validateOptionalDecimal(accuracyM, "accuracyM", 8, 2);
        validateOptionalDecimal(speedKmh, "speedKmh", 8, 2);
        validateOptionalDecimal(headingDeg, "headingDeg", 6, 2);

        if (accuracyM != null && accuracyM.signum() < 0) {
            throw new IllegalArgumentException("Invalid accuracyM");
        }

        if (speedKmh != null && speedKmh.signum() < 0) {
            throw new IllegalArgumentException("Invalid speedKmh");
        }

        if (headingDeg != null
                && (headingDeg.signum() < 0
                || headingDeg.compareTo(BigDecimal.valueOf(360)) >= 0)) {
            throw new IllegalArgumentException("Invalid headingDeg");
        }

        if (measuredAt == null) {
            throw new IllegalArgumentException("measuredAt is required");
        }

        LocalDateTime receivedAt = LocalDateTime.now(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MILLIS);

        // MariaDB DATETIME(3): chuan hoa GPS ve mili giay.
        measuredAt = measuredAt.truncatedTo(ChronoUnit.MILLIS);

        // Không chấp nhận thời gian GPS vượt quá hiện tại 5 phút.
        if (measuredAt.isAfter(receivedAt.plusMinutes(5))) {
            throw new IllegalArgumentException("GPS measuredAt is in the future");
        }

        DeviceEntity device = deviceRepository
                .findByDeviceCode(deviceCode.trim())
                .orElseThrow(() ->
                        new IllegalArgumentException("GPS device not found")
                );

        String normalizedMessageId = messageId.trim();

        // Xử lý bản tin trùng đã được lưu trước đó.
        var existing = locationRepository.findByDevice_IdAndMessageId(
                device.getId(),
                normalizedMessageId
        );

        if (existing.isPresent()) {
            DeviceLocationEntity stored = existing.get();

            // Cùng messageId phải có cùng dữ liệu GPS.
            assertSameGpsPayload(
                    stored,
                    latitude,
                    longitude,
                    accuracyM,
                    speedKmh,
                    headingDeg,
                    measuredAt
            );

            return stored;
        }


        DeviceLocationEntity location = new DeviceLocationEntity(
                device,
                normalizedMessageId,
                latitude,
                longitude,
                accuracyM,
                speedKmh,
                headingDeg,
                measuredAt,
                receivedAt
        );

        // Tìm tối đa 2 Trip tại thời điểm GPS được đo.
        // Không sử dụng receivedAt vì GPS có thể được gửi đến muộn.
        Page<TripEntity> matchingTrips = tripRepository.findTripsAtTime(
                device.getId(),
                measuredAt,
                PageRequest.of(0, 2)
        );

        if (matchingTrips.getTotalElements() > 1) {
            throw new IllegalStateException(
                    "Multiple trips overlap GPS measuredAt"
            );
        }

        if (matchingTrips.hasContent()) {
            TripEntity trip = matchingTrips.getContent().get(0);
            location.setTrip(trip);
        }

        // GPS ngoài Trip vẫn được lưu với trip_id = NULL.
        // Unique constraint bảo vệ khi nhận trùng messageId.
        return locationRepository.save(location);

    }


    @Override
    @Transactional(readOnly = true)
    public Optional<DeviceLocationEntity> getLatestLocation(
            Long deviceId
    ) {
        if (deviceId == null || deviceId <= 0) {
            throw new IllegalArgumentException(
                    "Invalid deviceId"
            );
        }

        return locationRepository
                .findFirstByDevice_IdOrderByMeasuredAtDescIdDesc(
                        deviceId
                );
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DeviceLocationEntity> getLocationHistory(
            Long deviceId,
            Pageable pageable
    ) {
        if (deviceId == null || deviceId <= 0) {
            throw new IllegalArgumentException(
                    "Invalid deviceId"
            );
        }

        if (pageable == null) {
            throw new IllegalArgumentException(
                    "Pageable is required"
            );
        }

        return locationRepository
                .findByDevice_IdOrderByMeasuredAtDescIdDesc(
                        deviceId,
                        pageable
                );
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DeviceLocationEntity> getTripLocationHistory(
            Long tripId,
            Pageable pageable
    ) {
        if (tripId == null || tripId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "tripId must be a positive number"
            );
        }

        // Phân biệt Trip không tồn tại với Trip chưa có dữ liệu GPS.
        if (!tripRepository.existsById(tripId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Trip not found: " + tripId
            );
        }

        // Trả về GPS theo thứ tự đo từ cũ đến mới.
        return locationRepository.findByTrip_IdOrderByMeasuredAtAscIdAsc(
                tripId,
                pageable
        );
    }


    private void assertSameGpsPayload(
            DeviceLocationEntity stored,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal accuracyM,
            BigDecimal speedKmh,
            BigDecimal headingDeg,
            LocalDateTime measuredAt
    ) {
        boolean same =
                sameDecimal(stored.getLatitude(), latitude)
                && sameDecimal(stored.getLongitude(), longitude)
                && sameDecimal(stored.getAccuracyM(), accuracyM)
                && sameDecimal(stored.getSpeedKmh(), speedKmh)
                && sameDecimal(stored.getHeadingDeg(), headingDeg)
                && java.util.Objects.equals(
                        stored.getMeasuredAt(), measuredAt
                );

        if (!same) {
            throw new IllegalArgumentException(
                    "GPS messageId conflict: payload differs"
            );
        }
    }

    private boolean sameDecimal(
            BigDecimal left,
            BigDecimal right
    ) {
        if (left == null || right == null) {
            return left == null && right == null;
        }

        return left.compareTo(right) == 0;
    }
    private void validateOptionalDecimal(
            BigDecimal value,
            String field,
            int precision,
            int scale
    ) {
        if (value == null) {
            return;
        }

        if (value.scale() > scale
                || value.precision() - value.scale() > precision - scale) {
            throw new IllegalArgumentException(
                    "Invalid decimal precision: " + field
            );
        }
    }
}
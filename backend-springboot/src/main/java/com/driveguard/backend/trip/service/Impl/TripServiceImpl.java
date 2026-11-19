
package com.driveguard.backend.trip.service.Impl;

import com.driveguard.backend.device.entity.DeviceEntity;
import com.driveguard.backend.device.repository.DeviceRepository;
import com.driveguard.backend.deviceassignment.entity.DeviceAssignmentEntity;
import com.driveguard.backend.deviceassignment.repository.DeviceAssignmentRepository;
import com.driveguard.backend.driver.entity.DriverEntity;
import com.driveguard.backend.driver.repository.DriverRepository;
import com.driveguard.backend.trip.dto.response.TripResponse;
import com.driveguard.backend.trip.entity.TripEntity;
import com.driveguard.backend.trip.enums.TripStatus;
import com.driveguard.backend.trip.repository.TripRepository;
import com.driveguard.backend.trip.service.TripService;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TripServiceImpl implements TripService {

    private final TripRepository tripRepository;
    private final DeviceRepository deviceRepository;
    private final DriverRepository driverRepository;
    private final DeviceAssignmentRepository assignmentRepository;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public TripResponse startTrip(Long assignmentId) {
        requireId(assignmentId, "assignmentId");

        // Đọc trước để xác định thứ tự khóa Device -> Driver -> Assignment.
        DeviceAssignmentEntity existing = assignmentRepository
                .findById(assignmentId)
                .orElseThrow(() -> notFound("Không tìm thấy phân công"));

        Long deviceId = existing.getDevice().getId();
        Long driverId = existing.getDriver().getId();

        DeviceEntity device = deviceRepository
                .findByIdForUpdate(deviceId)
                .orElseThrow(() -> notFound("Không tìm thấy thiết bị"));

        DriverEntity driver = driverRepository
                .findByIdForUpdate(driverId)
                .orElseThrow(() -> notFound("Không tìm thấy tài xế"));

        DeviceAssignmentEntity assignment = assignmentRepository
                .findByIdForUpdate(assignmentId)
                .orElseThrow(() -> notFound("Không tìm thấy phân công"));

        if (!assignment.getDevice().getId().equals(deviceId)
                || !assignment.getDriver().getId().equals(driverId)) {
            throw new IllegalStateException(
                    "Thông tin phân công đã thay đổi"
            );
        }

        LocalDateTime now = utcNow();

        if (assignment.getStartedAt().isAfter(now)
                || (assignment.getEndedAt() != null
                && !assignment.getEndedAt().isAfter(now))) {
            throw new IllegalStateException(
                    "Phân công không có hiệu lực tại thời điểm bắt đầu"
            );
        }

        if (device.getStatus() == null
                || device.getStatus() == DeviceEntity.DeviceStatus.INACTIVE
                || device.getStatus() == DeviceEntity.DeviceStatus.MAINTENANCE) {
            throw new IllegalStateException(
                    "Thiết bị không đủ điều kiện bắt đầu chuyến"
            );
        }

        if (driver.getUser() == null
                || !Boolean.TRUE.equals(driver.getUser().getActive())
                || driver.getUser().getRole() == null
                || !"DRIVER".equals(driver.getUser().getRole().getName())) {
            throw new IllegalStateException(
                    "Tài xế không hợp lệ hoặc tài khoản không hoạt động"
            );
        }

        // Chặn cả Trip đang chạy và lịch sử có thời gian chồng lấn.
        if (tripRepository.countOverlappingOpenTrip(
                deviceId, driverId, now) > 0) {
            throw new IllegalStateException(
                    "Thiết bị hoặc tài xế có chuyến đi chồng lấn"
            );
        }

        TripEntity trip = new TripEntity();
        trip.setAssignment(assignment);
        trip.setDevice(device);
        trip.setDriver(driver);
        trip.setStatus(TripStatus.IN_PROGRESS);
        trip.setStartedAt(now);
        trip.setEndedAt(null);

        TripEntity saved = tripRepository.saveAndFlush(trip);
        entityManager.refresh(saved);

        return TripResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public TripResponse completeTrip(Long tripId) {
        return finishTrip(tripId, TripStatus.COMPLETED);
    }

    @Override
    @Transactional
    public TripResponse cancelTrip(Long tripId) {
        return finishTrip(tripId, TripStatus.CANCELLED);
    }

    // Hoàn thành hoặc hủy chuyến trong cùng một transaction.
    private TripResponse finishTrip(Long tripId, TripStatus targetStatus) {
        requireId(tripId, "tripId");

        TripEntity existing = tripRepository.findById(tripId)
                .orElseThrow(() -> notFound("Không tìm thấy chuyến đi"));

        Long deviceId = existing.getDevice().getId();
        Long driverId = existing.getDriver().getId();
        Long assignmentId = existing.getAssignment().getId();

        // Giữ cùng thứ tự khóa với DeviceAssignmentServiceImpl.
        deviceRepository.findByIdForUpdate(deviceId)
                .orElseThrow(() -> notFound("Không tìm thấy thiết bị"));

        driverRepository.findByIdForUpdate(driverId)
                .orElseThrow(() -> notFound("Không tìm thấy tài xế"));

        DeviceAssignmentEntity assignment = assignmentRepository
                .findByIdForUpdate(assignmentId)
                .orElseThrow(() -> notFound("Không tìm thấy phân công"));

        TripEntity trip = tripRepository.findByIdForUpdate(tripId)
                .orElseThrow(() -> notFound("Không tìm thấy chuyến đi"));

        if (trip.getStatus() != TripStatus.IN_PROGRESS
                || trip.getEndedAt() != null) {
            throw new IllegalStateException(
                    "Chuyến đi không còn ở trạng thái IN_PROGRESS"
            );
        }

        LocalDateTime now = utcNow();

        if (!now.isAfter(trip.getStartedAt())) {
            throw new IllegalStateException(
                    "Thời điểm kết thúc phải sau thời điểm bắt đầu"
            );
        }

        if (assignment.getEndedAt() != null
                && now.isAfter(assignment.getEndedAt())) {
            throw new IllegalStateException(
                    "Chuyến đi vượt quá thời gian phân công"
            );
        }

        trip.setEndedAt(now);
        trip.setStatus(targetStatus);

        TripEntity saved = tripRepository.saveAndFlush(trip);
        entityManager.refresh(saved);

        return TripResponse.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TripResponse> getTrip(Long tripId) {
        requireId(tripId, "tripId");
        return tripRepository.findById(tripId)
                .map(TripResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TripResponse> getActiveTrip(Long deviceId) {
        requireId(deviceId, "deviceId");
        return tripRepository
                .findByDevice_IdAndStatus(deviceId, TripStatus.IN_PROGRESS)
                .map(TripResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TripResponse> getDeviceTrips(
            Long deviceId, Pageable pageable) {
        requireId(deviceId, "deviceId");
        return tripRepository
                .findByDevice_IdOrderByStartedAtDescIdDesc(deviceId, pageable)
                .map(TripResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TripResponse> getDriverTrips(
            Long driverId, Pageable pageable) {
        requireId(driverId, "driverId");
        return tripRepository
                .findByDriver_IdOrderByStartedAtDescIdDesc(driverId, pageable)
                .map(TripResponse::fromEntity);
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MICROS);
    }

    private void requireId(Long id, String name) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    name + " phải là số nguyên dương"
            );
        }
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}

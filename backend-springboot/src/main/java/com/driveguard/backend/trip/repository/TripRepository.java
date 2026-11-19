
package com.driveguard.backend.trip.repository;

import com.driveguard.backend.trip.entity.TripEntity;
import com.driveguard.backend.trip.enums.TripStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface TripRepository extends JpaRepository<TripEntity, Long> {

    // Tìm chuyến đang chạy theo thiết bị.
    Optional<TripEntity> findByDevice_IdAndStatus(
            Long deviceId,
            TripStatus status
    );

    // Tìm chuyến đang chạy theo tài xế.
    Optional<TripEntity> findByDriver_IdAndStatus(
            Long driverId,
            TripStatus status
    );

    // Lấy lịch sử chuyến đi theo thiết bị.
    Page<TripEntity> findByDevice_IdOrderByStartedAtDescIdDesc(
            Long deviceId,
            Pageable pageable
    );

    // Lấy lịch sử chuyến đi theo tài xế.
    Page<TripEntity> findByDriver_IdOrderByStartedAtDescIdDesc(
            Long driverId,
            Pageable pageable
    );

    // Kiểm tra có chuyến đang chạy thuộc Assignment hay không.
    boolean existsByAssignment_IdAndStatus(
            Long assignmentId,
            TripStatus status
    );

    // Khóa chuyến đi khi kết thúc để tránh xử lý đồng thời.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT t
            FROM TripEntity t
            WHERE t.id = :tripId
            """)
    Optional<TripEntity> findByIdForUpdate(
            @Param("tripId") Long tripId
    );

    // Kiểm tra chuyến lịch sử chồng lấn khoảng thời gian mới.
    @Query("""
            SELECT COUNT(t)
            FROM TripEntity t
            WHERE (t.device.id = :deviceId
                   OR t.driver.id = :driverId)
              AND t.startedAt < :endExclusive
              AND (t.endedAt IS NULL
                   OR t.endedAt > :startInclusive)
            """)
    long countOverlappingTrips(
            @Param("deviceId") Long deviceId,
            @Param("driverId") Long driverId,
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive
    );

    // Tìm Trip theo thời gian đo GPS.
    @Query("""
            SELECT t
            FROM TripEntity t
            WHERE t.device.id = :deviceId
              AND t.startedAt <= :measuredAt
              AND (t.endedAt IS NULL
                   OR t.endedAt > :measuredAt)
            ORDER BY t.startedAt DESC, t.id DESC
            """)
    Page<TripEntity> findTripsAtTime(
            @Param("deviceId") Long deviceId,
            @Param("measuredAt") LocalDateTime measuredAt,
            Pageable pageable
    );


    // Kiểm tra chuyến đang chạy mới có chồng lấn với Trip cũ hay không.
    // Khoảng thời gian mới là [startedAt, +vo_cuc).
    @Query("""
            SELECT COUNT(t)
            FROM TripEntity t
            WHERE (t.device.id = :deviceId
                   OR t.driver.id = :driverId)
              AND (t.endedAt IS NULL
                   OR t.endedAt > :startedAt)
            """)
    long countOverlappingOpenTrip(
            @Param("deviceId") Long deviceId,
            @Param("driverId") Long driverId,
            @Param("startedAt") LocalDateTime startedAt
    );

    // Kiểm tra có Trip đang chạy của Assignment hay không.
    @Query("""
            SELECT COUNT(t)
            FROM TripEntity t
            WHERE t.assignment.id = :assignmentId
              AND t.status = :status
            """)
    long countTripsByAssignmentAndStatus(
            @Param("assignmentId") Long assignmentId,
            @Param("status") TripStatus status
    );


    // Đếm Trip có thời gian nằm ngoài mốc kết thúc Assignment.
    @Query("""
            SELECT COUNT(t)
            FROM TripEntity t
            WHERE t.assignment.id = :assignmentId
              AND (
                  t.startedAt >= :assignmentEndedAt
                  OR t.endedAt IS NULL
                  OR t.endedAt > :assignmentEndedAt
              )
            """)
    long countTripsOutsideAssignmentEnd(
            @Param("assignmentId") Long assignmentId,
            @Param("assignmentEndedAt") LocalDateTime assignmentEndedAt
    );

}

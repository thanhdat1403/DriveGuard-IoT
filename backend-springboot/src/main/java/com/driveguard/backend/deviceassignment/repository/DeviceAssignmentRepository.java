
package com.driveguard.backend.deviceassignment.repository;

import com.driveguard.backend.deviceassignment.entity.DeviceAssignmentEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceAssignmentRepository
        extends JpaRepository<DeviceAssignmentEntity, Long> {

    /**
     * Tìm phân công đang hoạt động của thiết bị.
     *
     * endedAt = null nghĩa là chưa kết thúc.
     */
    Optional<DeviceAssignmentEntity>
    findByDevice_IdAndEndedAtIsNull(Long deviceId);

    /**
     * Tìm phân công đang hoạt động của tài xế.
     */
    Optional<DeviceAssignmentEntity>
    findByDriver_IdAndEndedAtIsNull(Long driverId);

    /**
     * Kiểm tra thiết bị đang có phân công hay không.
     */
    boolean existsByDevice_IdAndEndedAtIsNull(Long deviceId);

    /**
     * Kiểm tra tài xế đang có phân công hay không.
     */
    boolean existsByDriver_IdAndEndedAtIsNull(Long driverId);

    /**
     * Lấy lịch sử phân công của thiết bị,
     * mới nhất trước.
     */
    List<DeviceAssignmentEntity>
    findByDevice_IdOrderByStartedAtDescIdDesc(Long deviceId);

    /**
     * Lấy lịch sử phân công của tài xế,
     * mới nhất trước.
     */
    List<DeviceAssignmentEntity>
    findByDriver_IdOrderByStartedAtDescIdDesc(Long driverId);

    /**
     * Kiểm tra khoảng thời gian phân công
     * có chồng lấn với phân công khác
     * của cùng thiết bị hay không.
     *
     * Khoảng thời gian sử dụng dạng:
     * [startedAt, endedAt)
     *
     * endedAt = null nghĩa là không có
     * thời điểm kết thúc.
     */
    @Query("""
        SELECT COUNT(a)
        FROM DeviceAssignmentEntity a
        WHERE a.device.id = :deviceId
          AND (:endedAt IS NULL OR a.startedAt < :endedAt)
          AND (a.endedAt IS NULL OR a.endedAt > :startedAt)
        """)
    long countOverlappingDeviceAssignments(
            @Param("deviceId") Long deviceId,
            @Param("startedAt") LocalDateTime startedAt,
            @Param("endedAt") LocalDateTime endedAt
    );

    /**
     * Kiểm tra khoảng thời gian phân công
     * có chồng lấn với phân công khác
     * của cùng tài xế hay không.
     */
    @Query("""
        SELECT COUNT(a)
        FROM DeviceAssignmentEntity a
        WHERE a.driver.id = :driverId
          AND (:endedAt IS NULL OR a.startedAt < :endedAt)
          AND (a.endedAt IS NULL OR a.endedAt > :startedAt)
        """)
    long countOverlappingDriverAssignments(
            @Param("driverId") Long driverId,
            @Param("startedAt") LocalDateTime startedAt,
            @Param("endedAt") LocalDateTime endedAt
    );

    /**
     * Xác định phân công của thiết bị
     * tại một thời điểm cụ thể.
     *
     * Phục vụ việc liên kết dữ liệu cảm biến
     * với đúng phiên giám sát.
     */
    @Query("""
        SELECT a
        FROM DeviceAssignmentEntity a
        WHERE a.device.id = :deviceId
          AND a.startedAt <= :measuredAt
          AND (a.endedAt IS NULL OR a.endedAt > :measuredAt)
        ORDER BY a.startedAt DESC, a.id DESC
        """)
    List<DeviceAssignmentEntity> findAssignmentsAtTime(
            @Param("deviceId") Long deviceId,
            @Param("measuredAt") LocalDateTime measuredAt
    );



    /**
     * Khóa bản ghi phân công trước khi cập nhật.
     *
     * Chỉ gọi bên trong transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    SELECT a
    FROM DeviceAssignmentEntity a
    WHERE a.id = :assignmentId
    """)
    Optional<DeviceAssignmentEntity> findByIdForUpdate(
            @Param("assignmentId") Long assignmentId
    );


    /**
     * Tìm các phân công có hiệu lực của thiết bị
     * tại một thời điểm cụ thể.
     */
    @Query("""
    SELECT a
    FROM DeviceAssignmentEntity a
    WHERE a.device.id = :deviceId
      AND a.startedAt <= :atTime
      AND (a.endedAt IS NULL OR a.endedAt > :atTime)
    ORDER BY a.startedAt DESC, a.id DESC
    """)
    List<DeviceAssignmentEntity> findActiveByDeviceAtTime(
            @Param("deviceId") Long deviceId,
            @Param("atTime") LocalDateTime atTime
    );

    /**
     * Tìm các phân công có hiệu lực của tài xế
     * tại một thời điểm cụ thể.
     */
    @Query("""
    SELECT a
    FROM DeviceAssignmentEntity a
    WHERE a.driver.id = :driverId
      AND a.startedAt <= :atTime
      AND (a.endedAt IS NULL OR a.endedAt > :atTime)
    ORDER BY a.startedAt DESC, a.id DESC
    """)
    List<DeviceAssignmentEntity> findActiveByDriverAtTime(
            @Param("driverId") Long driverId,
            @Param("atTime") LocalDateTime atTime
    );


}

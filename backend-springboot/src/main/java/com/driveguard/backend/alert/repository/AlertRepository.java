
package com.driveguard.backend.alert.repository;

import com.driveguard.backend.alert.entity.AlertEntity;
import com.driveguard.backend.alert.enums.AlertEpisodeStatus;
import com.driveguard.backend.alert.enums.AlertStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface AlertRepository
        extends JpaRepository<AlertEntity, Long> {

    /**
     * Tìm đợt cảnh báo mới nhất đang diễn ra
     * theo thiết bị và loại cảnh báo.
     *
     * Không phụ thuộc vào trạng thái xử lý của người dùng.
     */
    Optional<AlertEntity>
    findFirstByDeviceIdAndAlertTypeAndEpisodeStatusOrderByStartedAtDescIdDesc(
            Long deviceId,
            String alertType,
            AlertEpisodeStatus episodeStatus
    );

    /**
     * Kiểm tra thiết bị có đợt cảnh báo
     * đang hoạt động hay không.
     */
    boolean existsByDeviceIdAndAlertTypeAndEpisodeStatus(
            Long deviceId,
            String alertType,
            AlertEpisodeStatus episodeStatus
    );

    /**
     * Tìm cảnh báo mới nhất của thiết bị
     * theo loại cảnh báo.
     */
    Optional<AlertEntity>
    findFirstByDeviceIdAndAlertTypeOrderByStartedAtDesc(
            Long deviceId,
            String alertType
    );

    /**
     * Kiểm tra một reading đã được liên kết
     * với cảnh báo hay chưa.
     */
    boolean existsByReadingId(Long readingId);

    /**
     * Danh sách cảnh báo theo thiết bị.
     */
    List<AlertEntity>
    findByDeviceIdOrderByStartedAtDesc(Long deviceId);

    /**
     * Danh sách cảnh báo theo trạng thái xử lý.
     */
    List<AlertEntity>
    findByStatusOrderByStartedAtDesc(AlertStatus status);

    // Phương thức trả về các cảnh báo SOS còn ACTIVE của 1 thiết bị, bất kể SOS được kích hoạt thủ công hay do không phản hồi
    @Query("""
    SELECT a
    FROM AlertEntity a
    WHERE a.deviceId = :deviceId
      AND a.alertType IN ('MANUAL_SOS', 'NO_RESPONSE_SOS')
      AND a.episodeStatus = :episodeStatus
    ORDER BY a.startedAt DESC, a.id DESC
    """)
    List<AlertEntity> findActiveSosAlerts(
            @Param("deviceId") Long deviceId,
            @Param("episodeStatus") AlertEpisodeStatus episodeStatus
    );

    /**
     * Tìm cảnh báo SOS theo thiết bị và định danh đợt SOS.
     *
     * Không lọc episodeStatus vì cần tìm được cả
     * những đợt đã kết thúc để chống xử lý sự kiện trễ.
     */
    Optional<AlertEntity> findByDeviceIdAndSosEpisodeId(
            Long deviceId,
            String sosEpisodeId
    );

    /**
     * Kiểm tra một định danh đợt SOS đã tồn tại.
     */
    boolean existsByDeviceIdAndSosEpisodeId(
            Long deviceId,
            String sosEpisodeId
    );


    Page<AlertEntity> findByDeviceId(
            Long deviceId,
            Pageable pageable
    );

    Page<AlertEntity> findByDeviceIdAndAlertTypeIn(
            Long deviceId,
            List<String> alertTypes,
            Pageable pageable
    );


    /**
     * Đếm tổng số cảnh báo của một thiết bị.
     *
     * Bao gồm cảnh báo ngủ gật và SOS.
     */
    long countByDeviceId(Long deviceId);


    /**
     * Đếm số cảnh báo SOS đang ACTIVE.
     *
     * Chỉ tính:
     * - MANUAL_SOS
     * - NO_RESPONSE_SOS
     *
     * Không phụ thuộc trạng thái xử lý NEW,
     * ACKNOWLEDGED hay RESOLVED.
     */
    @Query("""
        SELECT COUNT(a)
        FROM AlertEntity a
        WHERE a.deviceId = :deviceId
          AND a.alertType IN ('MANUAL_SOS', 'NO_RESPONSE_SOS')
          AND a.episodeStatus = :episodeStatus
        """)
    long countActiveSosAlerts(
            @Param("deviceId") Long deviceId,
            @Param("episodeStatus") AlertEpisodeStatus episodeStatus
    );


    /**
     * Đếm số cảnh báo chưa xử lý xong.
     *
     * Bao gồm:
     * - NEW
     * - ACKNOWLEDGED
     *
     * Không tính:
     * - RESOLVED
     * - FALSE_ALARM
     */
    @Query("""
        SELECT COUNT(a)
        FROM AlertEntity a
        WHERE a.deviceId = :deviceId
          AND a.status IN :statuses
        """)
    long countUnresolvedAlerts(
            @Param("deviceId") Long deviceId,
            @Param("statuses") List<AlertStatus> statuses
    );

}

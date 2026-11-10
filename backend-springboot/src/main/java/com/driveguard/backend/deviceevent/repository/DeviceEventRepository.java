
package com.driveguard.backend.deviceevent.repository;

import com.driveguard.backend.deviceevent.entity.DeviceEventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

@Repository
public interface DeviceEventRepository
        extends JpaRepository<DeviceEventEntity, Long> {

    // Kiểm tra một sự kiện đã được lưu hay chưa.
    // Dùng để hỗ trợ xử lý MQTT QoS 1 gửi trùng bản tin.
    boolean existsByEventId(String eventId);

    // Tìm sự kiện theo eventId.
    // Phục vụ kiểm tra và truy vết sự kiện.
    Optional<DeviceEventEntity> findByEventId(
            String eventId
    );

    // Lấy lịch sử sự kiện của một thiết bị,
    // sắp xếp từ mới nhất đến cũ nhất.
    Page<DeviceEventEntity>
    findByDeviceIdOrderByReceivedAtDescIdDesc(
            Long deviceId,
            Pageable pageable
    );

    // Lấy lịch sử theo loại sự kiện.
    Page<DeviceEventEntity>
    findByEventTypeOrderByReceivedAtDescIdDesc(
            String eventType,
            Pageable pageable
    );

    // Lấy lịch sử theo thiết bị và loại sự kiện.
    Page<DeviceEventEntity>
    findByDeviceIdAndEventTypeOrderByReceivedAtDescIdDesc(
            Long deviceId,
            String eventType,
            Pageable pageable
    );

    /**
     * Lấy lịch sử sự kiện của một đợt SOS.
     *
     * Sắp xếp theo thời gian thiết bị gửi sự kiện
     * và ID bản ghi để phục vụ kiểm tra, truy vết.
     */
    Page<DeviceEventEntity>
    findByDeviceIdAndSosEpisodeIdOrderByReceivedAtAscIdAsc(
            Long deviceId,
            String sosEpisodeId,
            Pageable pageable
    );


    /**
     * Kiểm tra một đợt SOS đã nhận sự kiện kết thúc hay chưa.
     *
     * Dùng để phát hiện SOS_ENDED đến trước sự kiện START
     * hoặc các bản tin START bị gửi lại.
     *
     * Không phụ thuộc vào thời điểm Backend nhận sự kiện.
     */
    boolean existsByDeviceIdAndSosEpisodeIdAndEventType(
            Long deviceId,
            String sosEpisodeId,
            String eventType
    );


    /**
     * Lấy sự kiện SOS_ENDED đầu tiên đã được Backend ghi nhận
     * cho một đợt SOS cụ thể.
     *
     * receivedAt là thời gian Backend nhận sự kiện,
     * KHÔNG phải thời gian thực tế trên ESP32-S3.
     */
    Optional<DeviceEventEntity>
    findFirstByDeviceIdAndSosEpisodeIdAndEventTypeOrderByReceivedAtAscIdAsc(
            Long deviceId,
            String sosEpisodeId,
            String eventType
    );


}

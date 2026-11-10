
package com.driveguard.backend.alert.service;

import com.driveguard.backend.alert.entity.AlertEntity;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AlertService {

    /**
     * Tạo cảnh báo ngủ gật nếu thiết bị chưa có
     * cảnh báo DROWSINESS đang hoạt động.
     *
     * @param deviceId  ID thiết bị
     * @param driverId  ID tài xế, có thể null
     * @param readingId ID bản ghi cảm biến
     * @param startedAt Thời điểm phát hiện
     * @return Cảnh báo vừa tạo, hoặc cảnh báo đang tồn tại
     */
    AlertEntity createDrowsinessAlertIfAbsent(
            Long deviceId,
            Long driverId,
            Long readingId,
            LocalDateTime startedAt
    );

    /**
     * Tìm cảnh báo ngủ gật chưa kết thúc
     * của một thiết bị.
     */
    Optional<AlertEntity> findActiveDrowsinessAlert(Long deviceId);


    /**
     * Kết thúc đợt cảnh báo ngủ gật đang hoạt động.
     *
     * Chỉ cập nhật episodeStatus và endedAt.
     * Không thay đổi AlertStatus.
     *
     * @param deviceId ID thiết bị
     * @param endedAt  Thời điểm nhận diện mắt trở lại bình thường (UTC)
     */
    void endActiveDrowsinessEpisode(
            Long deviceId,
            LocalDateTime endedAt
    );


    AlertEntity createSosAlertIfAbsent(
            Long deviceId,
            String eventType,
            LocalDateTime startedAt
    );

    Optional<AlertEntity> acknowledgeActiveSosAlert(
            Long deviceId
    );

    /**
     * Tạo cảnh báo SOS theo định danh của một đợt SOS.
     *
     * Nếu đợt SOS đã tồn tại, trả về cảnh báo cũ.
     * Không tạo thêm cảnh báo trùng.
     */
    AlertEntity createSosAlertIfAbsent(
            Long deviceId,
            String sosEpisodeId,
            String eventType,
            LocalDateTime startedAt
    );

    /**
     * Xác nhận đúng đợt SOS.
     *
     * Không xác nhận nhầm đợt SOS khác.
     */
    Optional<AlertEntity> acknowledgeSosAlert(
            Long deviceId,
            String sosEpisodeId
    );

    /**
     * Kết thúc một đợt SOS.
     *
     * Chỉ thay đổi episodeStatus và endedAt.
     * Không tự động chuyển status thành RESOLVED.
     */
    Optional<AlertEntity> endSosEpisode(
            Long deviceId,
            String sosEpisodeId,
            LocalDateTime endedAt
    );
}

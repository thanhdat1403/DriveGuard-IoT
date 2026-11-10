
package com.driveguard.backend.alert.enums;

/**
 * Trạng thái của một đợt phát hiện nguy hiểm.
 *
 * ACTIVE: Đợt cảnh báo đang diễn ra.
 * ENDED: Đợt cảnh báo đã kết thúc.
 *
 * Khác với AlertStatus, enum này không thể hiện
 * việc người dùng đã xác nhận hoặc xử lý cảnh báo.
 */
public enum AlertEpisodeStatus {

    ACTIVE,
    ENDED
}

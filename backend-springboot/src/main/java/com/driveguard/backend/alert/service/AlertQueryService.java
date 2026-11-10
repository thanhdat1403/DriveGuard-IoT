package com.driveguard.backend.alert.service;

import com.driveguard.backend.alert.dto.response.AlertResponse;
import org.springframework.data.domain.Page;
public interface AlertQueryService {

    /**
     * Truy vấn danh sách cảnh báo theo thiết bị.
     *
     * @param deviceId ID thiết bị
     * @param sosOnly true: chỉ lấy cảnh báo SOS
     * @param page Trang bắt đầu từ 0
     * @param size Số bản ghi trên mỗi trang
     * @return Danh sách cảnh báo đã chuyển sang DTO
     */

    Page<AlertResponse> getAlerts(
            Long deviceId,
            boolean sosOnly,
            int page,
            int size
    );
}

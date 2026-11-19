
package com.driveguard.backend.location.service;

import com.driveguard.backend.location.entity.DeviceLocationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

public interface DeviceLocationService {

    // Lưu GPS từ MQTT, giữ nguyên nghiệp vụ hiện tại.
    DeviceLocationEntity saveLocation(
            String deviceCode,
            String messageId,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal accuracyM,
            BigDecimal speedKmh,
            BigDecimal headingDeg,
            LocalDateTime measuredAt
    );

    // Lấy vị trí GPS mới nhất của thiết bị.
    Optional<DeviceLocationEntity> getLatestLocation(
            Long deviceId
    );

    // Lấy lịch sử GPS theo trang.
    Page<DeviceLocationEntity> getLocationHistory(
            Long deviceId,
            Pageable pageable
    );


    /**
     * Lấy lịch sử GPS của một chuyến đi theo thứ tự thời gian.
     * Trip không tồn tại sẽ được xử lý ở tầng Service.
     */
    Page<DeviceLocationEntity> getTripLocationHistory(
            Long tripId,
            Pageable pageable
    );

}

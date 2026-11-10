package com.driveguard.backend.alert.service.Impl;

import com.driveguard.backend.alert.dto.response.AlertResponse;
import com.driveguard.backend.alert.entity.AlertEntity;
import com.driveguard.backend.alert.repository.AlertRepository;
import com.driveguard.backend.alert.service.AlertQueryService;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
@Service
@RequiredArgsConstructor
public class AlertQueryServiceImpl implements AlertQueryService {

    private final AlertRepository alertRepository;

    // Phương thức truy vấn để lấy danh sách cảnh báo theo thiết bị
    @Override
    public Page<AlertResponse> getAlerts(
            Long deviceId,
            boolean sosOnly,
            int page,
            int size
    ) {
        // 1. Kiểm tra ID thiết bị
        if (deviceId == null || deviceId <= 0){
            throw new IllegalArgumentException(
                    "deviceId must be positive"
            );
        }

        // 2. Kiểm tra tham số phân trang
        if (page < 0 || size < 1 || size > 100){
            throw new IllegalArgumentException(
                    "Invalid pagination parameters"
            );
        }

        // 3. Sắp xếp cảnh báo mới nhất nên đầu
        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("startedAt"),
                        Sort.Order.desc("id")
                )
        );

        // 4. Truy vấn dữ liệu theo điều kiện
        Page<AlertEntity> result;

        if (sosOnly){
            result = alertRepository
                    .findByDeviceIdAndAlertTypeIn(
                            deviceId,
                            List.of(
                                    "MANUAL_SOS",
                                    "NO_RESPONSE_SOS"
                            ),
                            pageable
                    );
        } else {
            result = alertRepository.findByDeviceId(
                    deviceId,
                    pageable
            );
        }

        // 5. Chuyển Entity thành DTO trả về Frontend
        return result.map(AlertResponse::fromEntity);
    }
}

package com.driveguard.backend.dashboard.service.Impl;

import com.driveguard.backend.alert.enums.AlertEpisodeStatus;
import com.driveguard.backend.alert.enums.AlertStatus;
import com.driveguard.backend.alert.repository.AlertRepository;
import com.driveguard.backend.dashboard.dto.response.DashboardSummaryResponse;
import com.driveguard.backend.dashboard.service.DashboardService;
import com.driveguard.backend.device.repository.DeviceRepository;
import com.driveguard.backend.sensor.entity.SensorReadingEntity;
import com.driveguard.backend.sensor.repository.SensorReadingRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private final DeviceRepository deviceRepository;
    private final SensorReadingRepository sensorReadingRepository;
    private final AlertRepository alertRepository;

    // Lấy thống kê Dashboard theo thiết bị.
    @Override
    public DashboardSummaryResponse getSummary(Long deviceId) {

        if (deviceId == null || deviceId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "deviceId must be a positive number"
            );
        }

        // Kiểm tra thiết bị có tồn tại hay không
        if (!deviceRepository.existsById(deviceId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Device not found: " + deviceId
            );
        }

        long totalReadings =
                sensorReadingRepository.countByDevice_Id(deviceId);

        long totalAlerts =
                alertRepository.countByDeviceId(deviceId);

        // Đếm các đợt SOS đang hoạt động.
        long activeSosAlerts =
                alertRepository.countActiveSosAlerts(
                        deviceId,
                        AlertEpisodeStatus.ACTIVE
                );

        // Đếm cảnh báo chưa hoàn tất xử lý
        long unresolvedAlerts =
                alertRepository.countUnresolvedAlerts(
                        deviceId,
                        List.of(
                                AlertStatus.NEW,
                                AlertStatus.ACKNOWLEDGED
                        )
                );

        DashboardSummaryResponse.LatestSensor latestSensor =
                sensorReadingRepository
                        .findFirstByDevice_IdOrderByMeasuredAtDescIdDesc(
                                deviceId
                        )
                        .map(this::toLatestSensor)
                        .orElse(null);
        return new DashboardSummaryResponse(
                deviceId,
                totalReadings,
                totalAlerts,
                activeSosAlerts,
                unresolvedAlerts,
                latestSensor
        );
    }

    // Chuyển dữ liệu cảm biến sang DTO
    private DashboardSummaryResponse.LatestSensor toLatestSensor(
            SensorReadingEntity reading
    ) {
        return new DashboardSummaryResponse.LatestSensor(
                reading.getId(),
                toDouble(reading.getHeartRate()),
                toDouble(reading.getSpo2()),
                reading.getEdgeAiStatus(),
                reading.getIsDrowsy(),
                reading.getMeasuredAt()
        );
    }

    private Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}

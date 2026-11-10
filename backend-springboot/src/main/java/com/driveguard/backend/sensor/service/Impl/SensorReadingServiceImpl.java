package com.driveguard.backend.sensor.service.Impl;

import com.driveguard.backend.device.entity.DeviceEntity;
import com.driveguard.backend.device.repository.DeviceRepository;
import com.driveguard.backend.sensor.dto.response.SensorReadingResponse;
import com.driveguard.backend.sensor.entity.SensorReadingEntity;
import com.driveguard.backend.sensor.repository.SensorReadingRepository;
import com.driveguard.backend.sensor.service.SensorReadingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.driveguard.backend.sensor.dto.result.RiskAssessmentResult;
import com.driveguard.backend.sensor.service.RiskAssessmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.driveguard.backend.alert.service.AlertService;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class SensorReadingServiceImpl implements SensorReadingService {

    // Repository quản lý thông tin thiết bị trong bảng devices
    private final DeviceRepository deviceRepository;

    // Repository quản lý dữ liệu cảm biến trong bảng sensor_readings
    private final SensorReadingRepository sensorReadingRepository;

    // Service đánh giá rủi ro từ dữ liệu cảm biến và Edge AI.
    // Spring Boot tự động inject thông qua @RequiredArgsConstructor.
    private final RiskAssessmentService riskAssessmentService;

    // Service quản lý cảnh báo của hệ thống
    private final AlertService alertService;


    /**
     * Lưu một bản ghi dữ liệu cảm biến vào MariaDB.
     *
     * @Transactional đảm bảo các thao tác database
     * trong phương thức được thực hiện trong một transaction.
     */
    @Override
    @Transactional
    public SensorReadingEntity saveReading(
            String deviceCode,
            LocalDateTime measuredAt,
            BigDecimal heartRate,
            BigDecimal spo2,
            String edgeAiStatus,
            BigDecimal edgeAiConfidence,
            Boolean isDrowsy,
            String rawPayload
    ) {

        // Bước 1: Kiểm tra mã thiết bị
        if (deviceCode == null || deviceCode.isBlank()){
            throw new IllegalArgumentException(
                    "Ma thiet bi khong hop le"
            );
        }

        // Bước 2: Tìm thiết bị đã đăng ký trong database
        DeviceEntity device = deviceRepository
                .findByDeviceCode(deviceCode.trim())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Khong tim thay thiet bi: " + deviceCode
                ));

        // Bước 3: Kiểm tra trạng thái thiết bị
        if (device.getStatus() == DeviceEntity.DeviceStatus.INACTIVE
                || device.getStatus() == DeviceEntity.DeviceStatus.MAINTENANCE
        ){

            throw new IllegalStateException(
                    "Thiet bi khong duoc phep gui du lieu: " + deviceCode
            );
        }

        // Bước 4: Kiểm tra thời điểm đo
        if (measuredAt == null){
            throw new IllegalArgumentException(
                    "Thoi diem do khong duoc de trong"
            );
        }

        // Bước 5: Kiểm tra giá trị nhịp tim
        if (heartRate != null
                && heartRate.compareTo(BigDecimal.ZERO) < 0
        ) {
            throw new IllegalArgumentException(
                    "Nhịp tim không được âm"
            );
        }

        // BƯỚC 6: Kiểm tra giá trị SpO2
        if (spo2 != null
                && (spo2.compareTo(BigDecimal.ZERO) < 0
                || spo2.compareTo(new BigDecimal("100")) > 0)){

            throw new IllegalArgumentException(
                    "SpO2 phải nằm trong khoảng 0 đến 100"
            );
        }

        // BƯỚC 7: Kiểm tra độ tin cậy của AI
        if (edgeAiConfidence != null
                && (edgeAiConfidence.compareTo(BigDecimal.ZERO) < 0
                || edgeAiConfidence.compareTo(BigDecimal.ONE) > 0)){

            throw new IllegalArgumentException(
                    "Độ tin cậy của AI phải nằm trong khoảng 0 đến 1"
            );
        }

        // Chuan hoa du lieu PPG chua co phep do hop le.
        // Gia tri 0 tu Wokwi la sentinel "no contact".
        if (heartRate != null
                && heartRate.compareTo(BigDecimal.ZERO) == 0) {
            heartRate = null;
        }

        if (spo2 != null
                && spo2.compareTo(BigDecimal.ZERO) == 0) {
            spo2 = null;
        }

        // BƯỚC 8: Tạo đối tượng Entity
        SensorReadingEntity reading = new SensorReadingEntity();

        reading.setDevice(device);
        reading.setMeasuredAt(measuredAt);
        reading.setHeartRate(heartRate);
        reading.setSpo2(spo2);
        reading.setEdgeAiStatus(edgeAiStatus);
        reading.setEdgeAiConfidence(edgeAiConfidence);
        reading.setIsDrowsy(isDrowsy);
        reading.setRawPayload(rawPayload);

        // BƯỚC 9: Đánh giá rủi ro phía Server.
        //
        // Dữ liệu được chuyển đến RiskAssessmentService.
        // Kết quả trả về gồm:
        // - serverStatus: NORMAL, WARNING, DANGER, CRITICAL, UNKNOWN.
        // - riskLevel: LOW, MEDIUM, HIGH, CRITICAL hoặc null.
        //
        // Quy tắc hiện tại chỉ phục vụ mô phỏng và cảnh báo hỗ trợ.
        // Không phải kết quả chẩn đoán đột quỵ.
        RiskAssessmentResult assessmentResult = riskAssessmentService.assess(
                heartRate,
                spo2,
                edgeAiStatus,
                edgeAiConfidence,
                isDrowsy
        );

        // Gán trạng thái đánh giá vào Entity.
        // serverStatus được lưu dưới dạng String trong MariaDB.
        reading.setServerStatus(
                assessmentResult.getServerStatus().name()
        );

        // riskLevel được lưu bằng EnumType.STRING.
        // Nếu dữ liệu không đủ để đánh giá, riskLevel có thể null.
        reading.setRiskLevel(
                assessmentResult.getRiskLevel()
        );

        // Ghi log kết quả đánh giá.
        log.info(
                "[RISK] Device: {} | ServerStatus: {} | RiskLevel: {}",
                deviceCode,
                assessmentResult.getServerStatus(),
                assessmentResult.getRiskLevel()
        );

        // receivedAt và createdAt vẫn do MariaDB tự động tạo.


        // BƯỚC 10: Lưu dữ liệu cảm biến
        SensorReadingEntity savedReading =
                sensorReadingRepository.save(reading);

        log.info(
                "[SENSOR] Da luu du lieu | ID: {} | Device: {} | HR: {} | SpO2: {}",
                savedReading.getId(),
                deviceCode,
                heartRate,
                spo2
        );

        // BƯỚC 11: Kiểm tra cảnh báo ngủ gật
        //
        // Chỉ tạo cảnh báo khi:
        // - Edge AI báo DROWSY
        // - isDrowsy = true
        // - Server đánh giá WARNING
        // - Mức rủi ro MEDIUM

        boolean isDrowsinessWarning =
                "DROWSY".equalsIgnoreCase(edgeAiStatus)
                        && Boolean.TRUE.equals(isDrowsy)
                        && "WARNING".equals(
                        savedReading.getServerStatus()
                )
                        && savedReading.getRiskLevel() != null
                        && "MEDIUM".equals(
                        savedReading.getRiskLevel().name()
                );

        if (isDrowsinessWarning) {

            log.warn(
                    "[ALERT] Phat hien ngu gat | deviceCode={} | readingId={}",
                    deviceCode,
                    savedReading.getId()
            );

            alertService.createDrowsinessAlertIfAbsent(
                    device.getId(),
                    null,
                    savedReading.getId(),
                    savedReading.getMeasuredAt()
            );
        }


        // BƯỚC 12: Kiểm tra trạng thái mắt trở lại bình thường.
        //
        // Chỉ dựa vào dữ liệu Edge AI và cờ isDrowsy.
        // Không yêu cầu serverStatus = NORMAL vì PPG có thể
        // chưa tiếp xúc và serverStatus = UNKNOWN.

        boolean isEyesNormal =
                "NORMAL".equalsIgnoreCase(edgeAiStatus)
                        && Boolean.FALSE.equals(isDrowsy);

        if (isEyesNormal) {

            log.info(
                    "[ALERT] Mat tro lai binh thuong | deviceCode={} | readingId={}",
                    deviceCode,
                    savedReading.getId()
            );

            alertService.endActiveDrowsinessEpisode(
                    device.getId(),
                    savedReading.getMeasuredAt()
            );
        }


        // BƯỚC 12: Trả về bản ghi đã lưu
        return savedReading;

    }



    /**
     * Lấy bản ghi dữ liệu cảm biến mới nhất của một thiết bị.
     *
     * Quy trình:
     * 1. Kiểm tra deviceId.
     * 2. Kiểm tra thiết bị có tồn tại trong MariaDB.
     * 3. Truy vấn bản ghi cảm biến mới nhất.
     * 4. Chuyển Entity thành DTO Response.
     *
     * @Transactional(readOnly = true):
     * Chỉ đọc dữ liệu, không thực hiện cập nhật database.
     */
    @Override
    @Transactional(readOnly = true)
    public SensorReadingResponse getLatestReading(Long deviceId) {

        // Bước 1: Kểm tra ID thiết bị
        if (deviceId == null || deviceId <= 0){
            throw new IllegalArgumentException(
                    "ID thiet bi khong hop le"
            );
        }

        // Bước 2: Kiểm tra thiết bị tồn tại
        if (!deviceRepository.existsById(deviceId)){
            throw new IllegalArgumentException(
                    "Khong tim thay thiet bi voi ID: " + deviceId
            );
        }

        // Bước 3: Truy vấn bản ghi cảm biến mới nhất
        SensorReadingEntity reading = sensorReadingRepository
                .findFirstByDevice_IdOrderByMeasuredAtDescIdDesc(deviceId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Thiet bi chua co du lieu cam bien: " + deviceId
                ));

        // Bước 4: Ghi log phục vụ kiểm tra
        log.info(
                "[SENSOR] Lay du lieu moi nhat | Device ID: {} | Reading ID: {}",
                deviceId,
                reading.getId()
        );

        // BƯỚC 5: Chuyển Entity thành DTO
        return toResponse(reading);
    }

    /**
     * Lấy lịch sử dữ liệu cảm biến của một thiết bị.
     *
     * Hỗ trợ phân trang thông qua Pageable.
     * Repository sắp xếp theo measuredAt DESC và id DESC.
     *
     * Mỗi Entity được chuyển thành SensorReadingResponse
     * trước khi trả về cho Controller.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<SensorReadingResponse> getReadingHistory(
            Long deviceId,
            Pageable pageable
    ) {

        // Bước 1: Kiểm tra ID thiết bị
        if (deviceId == null || deviceId <= 0){
            throw new IllegalArgumentException(
                    "ID thiet bi khong hop le"
            );
        }

        // Bước 2: Kiểm tra thông tin phân trang
        if (pageable == null) {
            throw new IllegalArgumentException(
                    "Thong tin phan trang khong duoc de trong"
            );
        }

        // BƯỚC 3: Kiểm tra thiết bị có tồn tại
        if (!deviceRepository.existsById(deviceId)) {
            throw new IllegalArgumentException(
                    "Khong tim thay thiet bi voi ID: " + deviceId
            );
        }

        // BƯỚC 4: Lấy dữ liệu có phân trang từ Repository
        Page<SensorReadingEntity> readings =
                sensorReadingRepository
                        .findByDevice_IdOrderByMeasuredAtDescIdDesc(
                                deviceId,
                                pageable
                        );

        // BƯỚC 5: Ghi log số lượng bản ghi
        log.info(
                "[SENSOR] Lay lich su | Device ID: {} | Page: {} | Total: {}",
                deviceId,
                pageable.getPageNumber(),
                readings.getTotalElements()
        );

        // BƯỚC 6: Chuyển từng Entity thành DTO Response
        return readings.map(this::toResponse);
    }

    /**
     * Chuyển SensorReadingEntity thành SensorReadingResponse.
     *
     * Không trả trực tiếp Entity ra REST API để:
     * - Tránh lộ cấu trúc Entity.
     * - Hạn chế lỗi Lazy Loading.
     * - Kiểm soát các trường dữ liệu gửi về React.
     *
     * Phương thức được gọi trong Transaction đang hoạt động,
     * nên có thể truy cập quan hệ LAZY của DeviceEntity.
     */
    private SensorReadingResponse toResponse(
            SensorReadingEntity reading
    ) {
        // Lấy thông tin thiết bị liên kết với bản ghi
        DeviceEntity device = reading.getDevice();

        // Xây dựng DTO Response bằng Lombok @Builder
        return SensorReadingResponse.builder()
                .id(reading.getId())
                .deviceId(device.getId())
                .deviceCode(device.getDeviceCode())
                .deviceName(device.getDeviceName())
                .measuredAt(reading.getMeasuredAt())
                .receivedAt(reading.getReceivedAt())
                .heartRate(reading.getHeartRate())
                .spo2(reading.getSpo2())
                .edgeAiStatus(reading.getEdgeAiStatus())
                .edgeAiConfidence(reading.getEdgeAiConfidence())
                .serverStatus(reading.getServerStatus())
                .riskLevel(
                        reading.getRiskLevel() != null
                                ? reading.getRiskLevel().name()
                                : null
                )
                .isDrowsy(reading.getIsDrowsy())
                .createdAt(reading.getCreatedAt())
                .build();
    }
}

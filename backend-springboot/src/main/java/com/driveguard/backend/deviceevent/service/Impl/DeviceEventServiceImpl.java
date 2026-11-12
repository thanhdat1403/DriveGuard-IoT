package com.driveguard.backend.deviceevent.service.Impl;

import com.driveguard.backend.device.entity.DeviceEntity;
import com.driveguard.backend.device.repository.DeviceRepository;
import com.driveguard.backend.deviceevent.exception.DeviceNotFoundException;
import com.driveguard.backend.deviceevent.dto.mqtt.DeviceEventMessage;
import com.driveguard.backend.deviceevent.entity.DeviceEventEntity;
import com.driveguard.backend.deviceevent.repository.DeviceEventRepository;
import com.driveguard.backend.deviceevent.service.DeviceEventService;
import com.driveguard.backend.alert.service.AlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;

@Slf4j // Tự động tạo biến 'log' dùng để ghi log (thư viện Lombok)
@Service // Đánh dấu lớp này là một Service Component của Spring
@RequiredArgsConstructor // Tự động tạo Constructor để nhúng (Inject) các Repository bên dưới
public class DeviceEventServiceImpl implements DeviceEventService {

    // Danh sách các loại sự kiện mà hệ thống hỗ trợ xử lý
    private static final Set<String> SUPPORTED_EVENT_TYPES = Set.of(
            "NO_RESPONSE_SOS",         // SOS khi tài xế không phản hồi
            "MANUAL_SOS",              // Tài xế nhấn nút SOS thủ công
            "SOS_ACKNOWLEDGED",        // Sự kiện xác nhận đã nhận tín hiệu SOS
            "SOS_SOUND_MUTED",         // Tắt âm thanh cảnh báo SOS
            "SOS_ENDED",               // Cảnh báo đã kết thúc
            "DROWSINESS_ACKNOWLEDGED"  // Tài xế bấm nút xác nhận tỉnh táo khi có cảnh báo ngủ gật
    );

    private final DeviceRepository deviceRepository;           // Thao tác với bảng Thiết bị
    private final DeviceEventRepository deviceEventRepository; // Thao tác với bảng Sự kiện thiết bị

    private final AlertService alertService;

    @Override
    @Transactional // Đảm bảo tính toàn vẹn dữ liệu, lỗi sẽ tự động rollback lại DB
    public DeviceEventEntity saveEvent(
            DeviceEventMessage message, // Đối tượng chứa dữ liệu sự kiện đã được chuyển đổi (parse)
            String rawPayload           // Chuỗi dữ liệu gốc dạng JSON/String nhận từ MQTT
    ) {
        // 1. Kiểm tra tính hợp lệ của toàn bộ dữ liệu đầu vào (Gọi hàm private phía dưới)
        validateMessage(message, rawPayload);

        // Loại bỏ khoảng trắng thừa ở hai đầu của các chuỗi dữ liệu quan trọng
        String eventId = message.getEventId().trim();
        String deviceCode = message.getDeviceId().trim();
        String eventType = message.getEventType().trim();

        String sosEpisodeId = message.getSosEpisodeId() == null
                ? null
                : message.getSosEpisodeId().trim();

        // 2. Tìm thiết bị theo mã gửi từ ESP32-S3.
        // Lấy khóa ghi ngay từ truy vấn đầu tiên.
        //
        // Không thực hiện SELECT thông thường trước khi khóa,
        // tránh tạo snapshot cũ trong transaction REPEATABLE READ.
        DeviceEntity device = deviceRepository
                .findByDeviceCodeForUpdate(deviceCode)
                .orElseThrow(() ->
                        new DeviceNotFoundException(deviceCode)
                );

        // 4. Kiểm tra trùng eventId SAU KHI lấy khóa.
        //
        // Điều này tránh hai transaction của cùng thiết bị
        // cùng vượt qua bước kiểm tra trùng trước khi lưu.
        DeviceEventEntity existing = deviceEventRepository
                .findByEventId(eventId)
                .orElse(null);

        if (existing != null) {

            log.info(
                    "[DEVICE-EVENT] Duplicate event ignored | eventId={}",
                    eventId
            );

            return existing;
        }

        // 4. Khởi tạo đối tượng thực thể (Entity) mới để chuẩn bị lưu vào cơ sở dữ liệu
        DeviceEventEntity event = new DeviceEventEntity();

        event.setEventId(eventId);
        event.setDeviceId(device.getId()); // Gắn ID cốt lõi của thiết bị lấy từ DB
        event.setEventType(eventType);
        event.setSosEpisodeId(sosEpisodeId);
        event.setDeviceUptimeMs(message.getTimestampMs()); // Thời gian chạy của chip (Uptime tính bằng mili-giây)

        // Do ESP32 chỉ cung cấp uptime chứ không có giờ UTC chính xác, tạm thời để trống thời gian xảy ra lỗi ở thiết bị
        event.setOccurredAt(null);

        // Ghi nhận thời gian chính xác mà Backend tiếp nhận sự kiện theo múi giờ chuẩn quốc tế UTC
        event.setReceivedAt(LocalDateTime.now(ZoneOffset.UTC));

        // Lưu lại toàn bộ chuỗi dữ liệu gốc phục vụ việc kiểm toán (audit) hoặc debug sau này
        event.setRawPayload(rawPayload);

        // 5. Tiến hành lưu bản ghi sự kiện vào cơ sở dữ liệu
        DeviceEventEntity saved = deviceEventRepository.save(event);

        // ==================== MQTT EVENT -> ALERT ====================


        switch (eventType) {

            case "MANUAL_SOS", "NO_RESPONSE_SOS" -> {

                if (sosEpisodeId == null) {
                    // Chế độ tương thích firmware cũ.
                    // Chỉ nên duy trì trong giai đoạn chuyển đổi.
                    alertService.createSosAlertIfAbsent(
                            device.getId(),
                            eventType,
                            saved.getReceivedAt()
                    );
                } else {
                    alertService.createSosAlertIfAbsent(
                            device.getId(),
                            sosEpisodeId,
                            eventType,
                            saved.getReceivedAt()
                    );
                }

                log.info(
                        "[DEVICE-EVENT] SOS started | eventId={} | episodeId={}",
                        eventId,
                        sosEpisodeId
                );
            }

            case "SOS_ACKNOWLEDGED" -> {

                if (sosEpisodeId == null) {
                    // Chỉ áp dụng tạm thời với firmware cũ.
                    alertService.acknowledgeActiveSosAlert(
                            device.getId()
                    );
                } else {
                    alertService.acknowledgeSosAlert(
                            device.getId(),
                            sosEpisodeId
                    );
                }

                log.info(
                        "[DEVICE-EVENT] SOS acknowledged | eventId={} | episodeId={}",
                        eventId,
                        sosEpisodeId
                );
            }

            case "SOS_SOUND_MUTED" -> {
                // Chỉ lưu sự kiện, không kết thúc đợt SOS.
                log.info(
                        "[DEVICE-EVENT] SOS sound muted | eventId={} | episodeId={}",
                        eventId,
                        sosEpisodeId
                );
            }


            case "SOS_ENDED" -> {

                // SOS_ENDED bắt buộc thuộc một episode cụ thể.
                // Validation phía trên đã kiểm tra điều kiện này.
                if (sosEpisodeId == null) {
                    throw new IllegalArgumentException(
                            "SOS_ENDED requires sosEpisodeId"
                    );
                }

                // Sự kiện END đã được lưu vào device_events.
                // Thử kết thúc Alert tương ứng.
                java.util.Optional<com.driveguard.backend.alert.entity.AlertEntity>
                        endedAlert = alertService.endSosEpisode(
                        device.getId(),
                        sosEpisodeId,
                        saved.getReceivedAt()
                );

                if (endedAlert.isPresent()) {

                    // Đã tìm thấy Alert tương ứng.
                    // Kiểm tra trạng thái thực tế thay vì mặc định đã kết thúc.
                    if (endedAlert.get().getEpisodeStatus()
                            == com.driveguard.backend.alert.enums.AlertEpisodeStatus.ENDED) {

                        log.info(
                                "[DEVICE-EVENT] SOS episode ENDED | " +
                                        "eventId={} | episodeId={} | alertId={}",
                                eventId,
                                sosEpisodeId,
                                endedAlert.get().getId()
                        );

                    } else {

                        log.warn(
                                "[DEVICE-EVENT] SOS END received but episode " +
                                        "remains ACTIVE | eventId={} | episodeId={} | alertId={}",
                                eventId,
                                sosEpisodeId,
                                endedAlert.get().getId()
                        );
                    }

                } else {

                    // END đến trước START.
                    // Sự kiện END đã được lưu trong device_events.
                    // Khi START đến sau, AlertServiceImpl sẽ truy vấn
                    // sự kiện END đã lưu để tạo episode ở trạng thái ENDED.
                    log.warn(
                            "[DEVICE-EVENT] SOS END stored before START | " +
                                    "eventId={} | deviceId={} | episodeId={}",
                            eventId,
                            device.getId(),
                            sosEpisodeId
                    );
                }
            }


            case "DROWSINESS_ACKNOWLEDGED" -> {
                log.info(
                        "[DEVICE-EVENT] Drowsiness acknowledged | eventId={}",
                        eventId
                );
            }

            default -> log.warn(
                    "[DEVICE-EVENT] Unhandled event type: {}",
                    eventType
            );
        }


        // Ghi log thông báo lưu sự kiện thành công thành công (Mức INFO)
        log.info(
                "[DEVICE-EVENT] Saved | id={} | eventId={} | device={} | type={}",
                saved.getId(),
                saved.getEventId(),
                deviceCode,
                eventType
        );

        return saved;
    }

    /**
     * Chức năng: Kiểm tra nghiêm ngặt tính hợp lệ và an toàn của dữ liệu đầu vào.
     * Tránh các lỗi dữ liệu quá dài, thiếu thông tin bắt buộc hoặc mã độc có kích thước lớn.
     */
    private void validateMessage(
            DeviceEventMessage message,
            String rawPayload
    ) {
        // Kiểm tra đối tượng thông điệp tổng không được phép null
        if (message == null) {
            throw new IllegalArgumentException(
                    "Device event message must not be null"
            );
        }

        // Kiểm tra Event ID không được để trống
        if (isBlank(message.getEventId())) {
            throw new IllegalArgumentException(
                    "eventId must not be blank"
            );
        }

        // Kiểm tra Event ID không được quá dài (Vượt quá giới hạn cột trong DB - 150 ký tự)
        if (message.getEventId().trim().length() > 150){
            throw new IllegalArgumentException(
                    "eventId exceeds 150 characters"
            );
        }

        // Kiểm tra Device ID (Mã thiết bị gửi lên) không được để trống
        if (isBlank(message.getDeviceId())) {
            throw new IllegalArgumentException(
                    "deviceId must not be blank"
            );
        }

        // Kiểm tra mã thiết bị không được vượt quá 50 ký tự
        if (message.getDeviceId().trim().length() > 50){
            throw new IllegalArgumentException(
                    "deviceId exceeds 50 characters"
            );
        }

        // Kiểm tra loại sự kiện: Không được rỗng VÀ phải nằm trong danh sách hỗ trợ (SUPPORTED_EVENT_TYPES)
        if (isBlank(message.getEventType())
                || !SUPPORTED_EVENT_TYPES.contains(
                message.getEventType().trim()
        )) {
            throw new IllegalArgumentException(
                    "Unsupported eventType: " + message.getEventType()
            );
        }

        String sosEpisodeId = message.getSosEpisodeId();

        if (sosEpisodeId != null) {
            if (sosEpisodeId.isBlank()
                    || sosEpisodeId.length() > 150
                    || !sosEpisodeId.equals(sosEpisodeId.trim())) {
                throw new IllegalArgumentException(
                        "Invalid sosEpisodeId"
                );
            }
        }

        if ("SOS_ENDED".equals(message.getEventType().trim())
                && sosEpisodeId == null) {
            throw new IllegalArgumentException(
                    "SOS_ENDED requires sosEpisodeId"
            );
        }

        // Kiểm tra Boot ID (Mã định danh mỗi lần khởi động lại của chip) không được để trống
        if (isBlank(message.getBootId())) {
            throw new IllegalArgumentException(
                    "bootId must not be blank"
            );
        }

        // Kiểm tra số thứ tự gói tin (Sequence): Bắt buộc phải có và lớn hơn 0
        if (message.getSequence() == null
                || message.getSequence() <= 0) {
            throw new IllegalArgumentException(
                    "sequence must be greater than zero"
            );
        }

        // Kiểm tra mốc Uptime (timestampMs): Không được phép âm
        if (message.getTimestampMs() == null
                || message.getTimestampMs() < 0) {
            throw new IllegalArgumentException(
                    "timestampMs must not be negative"
            );
        }

        // Kiểm tra chuỗi payload gốc không được để trống
        if (isBlank(rawPayload)) {
            throw new IllegalArgumentException(
                    "rawPayload must not be blank"
            );
        }

        // Giới hạn kích thước gói tin rawPayload ở mức tối đa là 16 KB nhằm bảo vệ bộ nhớ Server
        if (rawPayload.getBytes(
                java.nio.charset.StandardCharsets.UTF_8
        ).length > 16 * 1024) {
            throw new IllegalArgumentException(
                    "rawPayload exceeds 16 KB"
            );
        }
    }

    /**
     * Hàm tiện ích kiểm tra chuỗi rỗng hoặc chỉ chứa toàn dấu cách
     */
    private boolean isBlank(String value){
        return value == null || value.isBlank();
    }
}

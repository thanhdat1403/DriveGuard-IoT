package com.driveguard.backend.deviceevent.service.Impl;

import com.driveguard.backend.device.repository.DeviceRepository;
import com.driveguard.backend.deviceevent.dto.response.DeviceEventResponse;
import com.driveguard.backend.deviceevent.entity.DeviceEventEntity;
import com.driveguard.backend.deviceevent.repository.DeviceEventRepository;
import com.driveguard.backend.deviceevent.service.DeviceEventQueryService;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // Tối ưu hiệu năng vì class này chỉ đọc dữ liệu, không ghi/sửa
public class DeviceEventQueryServiceImpl
        implements DeviceEventQueryService {

    private final DeviceEventRepository eventRepository;
    private final DeviceRepository deviceRepository;

    private static final Set<String> SUPPORTED_EVENT_TYPES = Set.of(
            "MANUAL_SOS",
            "NO_RESPONSE_SOS",
            "SOS_ACKNOWLEDGED",
            "SOS_SOUND_MUTED",
            "SOS_ENDED",
            "DROWSINESS_ACKNOWLEDGED"
    );


    // Lấy danh sách sự kiện của thiết bị (có phân trang, lọc theo loại, xếp mới nhất lên đầu)
    @Override
    public Page<DeviceEventResponse> getDeviceEvents(
            Long deviceId,
            String eventType,
            int page,
            int size
    ) {

        validateDevice(deviceId);
        validatePagination(page, size);

        PageRequest pageable = PageRequest.of(page, size);
        String normalizedType = normalizeEventType(eventType);

        Page<DeviceEventEntity> events;
        if (normalizedType == null) {
            // Lấy tất cả loại sự kiện nếu không truyền eventType
            events = eventRepository.findByDeviceIdOrderByReceivedAtDescIdDesc(deviceId, pageable);

        } else {
            // Lọc theo loại sự kiện cụ thể
            events = eventRepository.findByDeviceIdAndEventTypeOrderByReceivedAtDescIdDesc(
                    deviceId, normalizedType, pageable
            );
        }

        // Chuyển đổi từ Entity sang DTO Response trước khi trả về
        return events.map(DeviceEventResponse::fromEntity);
    }

    // Tìm chi tiết 1 sự kiện dựa trên mã eventId
    @Override
    public DeviceEventResponse getEventByEventId(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId khong duoc de trong");
        }
        if (eventId.length() > 150){
            throw new IllegalArgumentException("eventId khong duoc vuot qua 150 ky tu");
        }

        // Tìm trong DB, nếu không có thì ném lỗi 404 Not Found
        DeviceEventEntity event = eventRepository
                .findByEventId(eventId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Khong tim thay su kien"
                ));

        return DeviceEventResponse.fromEntity(event);
    }

    // Lấy toàn bộ chuỗi sự kiện trong một phiên SOS (xếp theo thời gian tăng dần để xem diễn biến)
    @Override
    public Page<DeviceEventResponse> getSosEpisodeEvents(
            Long deviceId,
            String sosEpisodeId,
            int page,
            int size
    ) {
        validateDevice(deviceId);
        validatePagination(page, size);

        if (sosEpisodeId == null || sosEpisodeId.isBlank()) {
            throw new IllegalArgumentException("sosEpisodeId khong duoc de trong");
        }
        if (sosEpisodeId.length() > 150) {
            throw new IllegalArgumentException("sosEpisodeId khong duoc vuot qua 150 ky tu");
        }

        Page<DeviceEventEntity> events = eventRepository
                .findByDeviceIdAndSosEpisodeIdOrderByReceivedAtAscIdAsc(
                        deviceId, sosEpisodeId, PageRequest.of(page, size)
                );

        return events.map(DeviceEventResponse::fromEntity);
    }

    // Kiểm tra ID hợp lệ và thiết bị có tồn tại trong DB hay không
    private void validateDevice(Long deviceId) {
        if (deviceId == null || deviceId <= 0) {
            throw new IllegalArgumentException("deviceId phai lon hon 0");
        }
        if (!deviceRepository.existsById(deviceId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Khong tim thay thiet bi");
        }
    }

    // Giới hạn số trang và kích thước trang (1- 100) để tránh quá tải truy vấn
    private void validatePagination(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page phai lon hon hoac bang 0");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size phai nam trong khoang 1 den 100");
        }
    }

    // Cắt khoảng trắng thừa và kiểm tra độ dài của eventType

    private String normalizeEventType(String eventType) {
        if (eventType == null) {
            return null;
        }

        String normalized = eventType.trim();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "eventType khong duoc de trong"
            );
        }

        if (!SUPPORTED_EVENT_TYPES.contains(normalized)) {
            throw new IllegalArgumentException(
                    "eventType khong duoc ho tro: " + normalized
            );
        }

        return normalized;
    }

}

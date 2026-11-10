
package com.driveguard.backend.deviceevent.service;

import com.driveguard.backend.alert.service.AlertService;
import com.driveguard.backend.device.entity.DeviceEntity;
import com.driveguard.backend.device.repository.DeviceRepository;
import com.driveguard.backend.deviceevent.dto.mqtt.DeviceEventMessage;
import com.driveguard.backend.deviceevent.entity.DeviceEventEntity;
import com.driveguard.backend.deviceevent.repository.DeviceEventRepository;
import com.driveguard.backend.deviceevent.service.Impl.DeviceEventServiceImpl;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceEventServiceImplTest {

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private DeviceEventRepository deviceEventRepository;

    @Mock
    private AlertService alertService;

    @InjectMocks
    private DeviceEventServiceImpl deviceEventService;

    @Test
    void saveEvent_shouldStoreSosEndWhenStartHasNotArrived() {

        // 1. ARRANGE: Chuẩn bị dữ liệu kiểm thử
        Long deviceId = 1L;
        String deviceCode = "esp32s3-demo";
        String episodeId = "sos-test-end-first-001";
        String eventId = "event-end-first-001";

        DeviceEventMessage message = new DeviceEventMessage();

        message.setEventId(eventId);
        message.setDeviceId(deviceCode);
        message.setEventType("SOS_ENDED");
        message.setSosEpisodeId(episodeId);
        message.setTimestampMs(30000L);
        message.setBootId("boot-test-001");
        message.setSequence(2L);

        String rawPayload = """
                {
                  "eventId": "event-end-first-001",
                  "deviceId": "esp32s3-demo",
                  "eventType": "SOS_ENDED",
                  "sosEpisodeId": "sos-test-end-first-001",
                  "timestampMs": 30000,
                  "bootId": "boot-test-001",
                  "sequence": 2
                }
                """;

        // 2. Giả lập sự kiện chưa tồn tại trong DB
        when(deviceEventRepository.findByEventId(eventId))
                .thenReturn(Optional.empty());

        // 3. Giả lập thiết bị hợp lệ
        DeviceEntity device = new DeviceEntity();

        ReflectionTestUtils.setField(
                device,
                "id",
                deviceId
        );

        when(deviceRepository.findByDeviceCode(deviceCode))
                .thenReturn(Optional.of(device));

        // 4. Giả lập Repository lưu sự kiện thành công
        when(deviceEventRepository.save(any(DeviceEventEntity.class)))
                .thenAnswer(invocation -> {
                    DeviceEventEntity event =
                            invocation.getArgument(0);

                    ReflectionTestUtils.setField(
                            event,
                            "id",
                            100L
                    );

                    return event;
                });

        // 5. END đến trước START nên chưa tìm thấy Alert
        when(alertService.endSosEpisode(
                eq(deviceId),
                eq(episodeId),
                any(java.time.LocalDateTime.class)
        )).thenReturn(Optional.empty());

        // 6. ACT: Gọi phương thức cần kiểm thử
        DeviceEventEntity result =
                deviceEventService.saveEvent(
                        message,
                        rawPayload
                );

        // 7. ASSERT: Kiểm tra kết quả
        assertNotNull(result);
        assertEquals(100L, result.getId());
        assertEquals(eventId, result.getEventId());
        assertEquals(deviceId, result.getDeviceId());
        assertEquals("SOS_ENDED", result.getEventType());
        assertEquals(episodeId, result.getSosEpisodeId());
        assertEquals(30000L, result.getDeviceUptimeMs());

        assertNotNull(result.getReceivedAt());
        assertNull(result.getOccurredAt());

        // Xác nhận sự kiện được gửi tới Repository
        verify(deviceEventRepository, times(1))
                .save(any(DeviceEventEntity.class));

        // Xác nhận Backend yêu cầu kết thúc đúng episode
        verify(alertService, times(1))
                .endSosEpisode(
                        eq(deviceId),
                        eq(episodeId),
                        eq(result.getReceivedAt())
                );

        // Không được tạo Alert mới khi chỉ nhận END
        verify(alertService, never())
                .createSosAlertIfAbsent(
                        anyLong(),
                        anyString(),
                        anyString(),
                        any(java.time.LocalDateTime.class)
                );
    }


    @Test
    void saveEvent_shouldIgnoreDuplicateEventId() {

        // 1. ARRANGE: Chuẩn bị sự kiện MQTT gửi lại
        String eventId = "event-duplicate-001";
        String deviceCode = "esp32s3-demo";
        String episodeId = "sos-duplicate-001";

        DeviceEventMessage message = new DeviceEventMessage();

        message.setEventId(eventId);
        message.setDeviceId(deviceCode);
        message.setEventType("MANUAL_SOS");
        message.setSosEpisodeId(episodeId);
        message.setBootId("boot-test-001");
        message.setSequence(3L);
        message.setTimestampMs(45000L);

        String rawPayload = """
            {
              "eventId": "event-duplicate-001",
              "deviceId": "esp32s3-demo",
              "eventType": "MANUAL_SOS",
              "sosEpisodeId": "sos-duplicate-001",
              "bootId": "boot-test-001",
              "sequence": 3,
              "timestampMs": 45000
            }
            """;

        // 2. Giả lập sự kiện đã tồn tại trong database
        DeviceEventEntity existingEvent =
                new DeviceEventEntity();

        existingEvent.setId(200L);
        existingEvent.setEventId(eventId);
        existingEvent.setDeviceId(1L);
        existingEvent.setEventType("MANUAL_SOS");
        existingEvent.setSosEpisodeId(episodeId);

        when(deviceEventRepository.findByEventId(eventId))
                .thenReturn(Optional.of(existingEvent));

        // 3. ACT: Gửi lại sự kiện có cùng eventId
        DeviceEventEntity result =
                deviceEventService.saveEvent(
                        message,
                        rawPayload
                );

        // 4. ASSERT: Backend trả về bản ghi cũ
        assertNotNull(result);
        assertSame(existingEvent, result);
        assertEquals(200L, result.getId());

        // Không được lưu thêm bản ghi mới
        verify(deviceEventRepository, never())
                .save(any(DeviceEventEntity.class));

        // Không cần truy vấn lại thiết bị
        verifyNoInteractions(deviceRepository);

        // Không được tạo hoặc cập nhật Alert lần thứ hai
        verifyNoInteractions(alertService);

        // Chỉ kiểm tra sự kiện trùng đúng một lần
        verify(deviceEventRepository, times(1))
                .findByEventId(eventId);
    }


    @Test
    void saveEvent_shouldRouteStartArrivingAfterEndToAlertService() {

        // 1. ARRANGE: START được Backend nhận sau END
        Long deviceId = 1L;
        String deviceCode = "esp32s3-demo";
        String episodeId = "sos-test-end-first-002";
        String eventId = "event-start-late-001";

        DeviceEventMessage message = new DeviceEventMessage();

        message.setEventId(eventId);
        message.setDeviceId(deviceCode);
        message.setEventType("MANUAL_SOS");
        message.setSosEpisodeId(episodeId);
        message.setBootId("boot-test-001");
        message.setSequence(10L);
        message.setTimestampMs(30000L);

        String rawPayload = """
            {
              "eventId": "event-start-late-001",
              "deviceId": "esp32s3-demo",
              "eventType": "MANUAL_SOS",
              "sosEpisodeId": "sos-test-end-first-002",
              "bootId": "boot-test-001",
              "sequence": 10,
              "timestampMs": 30000
            }
            """;

        // 2. START này chưa tồn tại trong device_events
        when(deviceEventRepository.findByEventId(eventId))
                .thenReturn(Optional.empty());

        // 3. Thiết bị đã được đăng ký
        DeviceEntity device = new DeviceEntity();

        org.springframework.test.util.ReflectionTestUtils.setField(
                device,
                "id",
                deviceId
        );

        when(deviceRepository.findByDeviceCode(deviceCode))
                .thenReturn(Optional.of(device));

        // 4. Giả lập lưu START thành công
        when(deviceEventRepository.save(any(DeviceEventEntity.class)))
                .thenAnswer(invocation -> {

                    DeviceEventEntity event =
                            invocation.getArgument(0);

                    event.setId(301L);

                    return event;
                });

        // 5. ACT: Backend tiếp nhận START
        DeviceEventEntity result =
                deviceEventService.saveEvent(message, rawPayload);

        // 6. ASSERT: Kiểm tra dữ liệu START
        assertNotNull(result);
        assertEquals(301L, result.getId());
        assertEquals(eventId, result.getEventId());
        assertEquals("MANUAL_SOS", result.getEventType());
        assertEquals(episodeId, result.getSosEpisodeId());
        assertEquals(deviceId, result.getDeviceId());
        assertNotNull(result.getReceivedAt());

        // 7. Phải chuyển START đến AlertService với đúng episode
        verify(alertService, times(1))
                .createSosAlertIfAbsent(
                        eq(deviceId),
                        eq(episodeId),
                        eq("MANUAL_SOS"),
                        eq(result.getReceivedAt())
                );

        // 8. START không được trực tiếp gọi kết thúc SOS
        verify(alertService, never())
                .endSosEpisode(
                        anyLong(),
                        anyString(),
                        any(java.time.LocalDateTime.class)
                );

        // 9. START không được gọi ACK
        verify(alertService, never())
                .acknowledgeSosAlert(
                        anyLong(),
                        anyString()
                );

        // 10. Chỉ lưu sự kiện START một lần
        verify(deviceEventRepository, times(1))
                .save(any(DeviceEventEntity.class));
    }


    @Test
    void saveEvent_shouldRouteLateSosAcknowledgementToCorrectEpisode() {

        // 1. ARRANGE: ACK được nhận sau khi SOS đã kết thúc
        Long deviceId = 1L;
        String deviceCode = "esp32s3-demo";
        String episodeId = "sos-test-ended-001";
        String eventId = "event-late-ack-001";

        DeviceEventMessage message = new DeviceEventMessage();

        message.setEventId(eventId);
        message.setDeviceId(deviceCode);
        message.setEventType("SOS_ACKNOWLEDGED");
        message.setSosEpisodeId(episodeId);
        message.setBootId("boot-test-001");
        message.setSequence(12L);
        message.setTimestampMs(50000L);

        String rawPayload = """
            {
              "eventId": "event-late-ack-001",
              "deviceId": "esp32s3-demo",
              "eventType": "SOS_ACKNOWLEDGED",
              "sosEpisodeId": "sos-test-ended-001",
              "bootId": "boot-test-001",
              "sequence": 12,
              "timestampMs": 50000
            }
            """;

        // 2. ACK chưa tồn tại trong database
        when(deviceEventRepository.findByEventId(eventId))
                .thenReturn(Optional.empty());

        // 3. Thiết bị hợp lệ
        DeviceEntity device = new DeviceEntity();

        org.springframework.test.util.ReflectionTestUtils.setField(
                device,
                "id",
                deviceId
        );

        when(deviceRepository.findByDeviceCode(deviceCode))
                .thenReturn(Optional.of(device));

        // 4. Giả lập lưu ACK thành công
        when(deviceEventRepository.save(any(DeviceEventEntity.class)))
                .thenAnswer(invocation -> {

                    DeviceEventEntity event =
                            invocation.getArgument(0);

                    event.setId(401L);

                    return event;
                });

        // 5. ACT: Backend nhận ACK muộn
        DeviceEventEntity result =
                deviceEventService.saveEvent(message, rawPayload);

        // 6. ASSERT: Kiểm tra sự kiện được lưu đúng
        assertNotNull(result);
        assertEquals(401L, result.getId());
        assertEquals(eventId, result.getEventId());
        assertEquals("SOS_ACKNOWLEDGED", result.getEventType());
        assertEquals(episodeId, result.getSosEpisodeId());
        assertEquals(deviceId, result.getDeviceId());
        assertNotNull(result.getReceivedAt());

        // 7. ACK phải được chuyển đến đúng episode
        verify(alertService, times(1))
                .acknowledgeSosAlert(
                        eq(deviceId),
                        eq(episodeId)
                );

        // 8. ACK không được tạo Alert mới
        verify(alertService, never())
                .createSosAlertIfAbsent(
                        anyLong(),
                        anyString(),
                        anyString(),
                        any(java.time.LocalDateTime.class)
                );

        // 9. ACK không được gọi kết thúc episode
        verify(alertService, never())
                .endSosEpisode(
                        anyLong(),
                        anyString(),
                        any(java.time.LocalDateTime.class)
                );

        // 10. Sự kiện chỉ được lưu một lần
        verify(deviceEventRepository, times(1))
                .save(any(DeviceEventEntity.class));
    }

}

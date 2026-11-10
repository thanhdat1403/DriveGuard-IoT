
package com.driveguard.backend.alert.service;

import com.driveguard.backend.alert.entity.AlertEntity;
import com.driveguard.backend.alert.enums.AlertEpisodeStatus;
import com.driveguard.backend.alert.enums.AlertStatus;
import com.driveguard.backend.alert.repository.AlertRepository;
import com.driveguard.backend.alert.service.Impl.AlertServiceImpl;
import com.driveguard.backend.deviceevent.entity.DeviceEventEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.driveguard.backend.deviceevent.repository.DeviceEventRepository;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertServiceImplTest {

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private DeviceEventRepository deviceEventRepository;

    @InjectMocks
    private AlertServiceImpl alertService;

    private AlertEntity alert;
    private LocalDateTime startedAt;

    @BeforeEach
    void setUp() {
        startedAt = LocalDateTime.of(
                2026, 11, 4, 10, 0
        );

        alert = new AlertEntity();
        alert.setId(100L);
        alert.setDeviceId(1L);
        alert.setSosEpisodeId("sos-test-001");
        alert.setAlertType("MANUAL_SOS");
        alert.setStatus(AlertStatus.NEW);
        alert.setEpisodeStatus(AlertEpisodeStatus.ACTIVE);
        alert.setStartedAt(startedAt);
    }

    @Test
    void createSosAlert_shouldCreateNewEpisode() {

        when(alertRepository.findByDeviceIdAndSosEpisodeId(
                1L, "sos-test-001"
        )).thenReturn(Optional.empty());

        when(alertRepository.save(any(AlertEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AlertEntity result = alertService.createSosAlertIfAbsent(
                1L,
                "sos-test-001",
                "MANUAL_SOS",
                startedAt
        );

        assertNotNull(result);
        assertEquals("sos-test-001", result.getSosEpisodeId());
        assertEquals(AlertStatus.NEW, result.getStatus());
        assertEquals(AlertEpisodeStatus.ACTIVE, result.getEpisodeStatus());
        assertNull(result.getEndedAt());

        verify(alertRepository, times(1))
                .save(any(AlertEntity.class));
    }

    @Test
    void createSosAlert_shouldReturnExistingEpisode() {

        when(alertRepository.findByDeviceIdAndSosEpisodeId(
                1L, "sos-test-001"
        )).thenReturn(Optional.of(alert));

        AlertEntity result = alertService.createSosAlertIfAbsent(
                1L,
                "sos-test-001",
                "MANUAL_SOS",
                startedAt
        );

        assertEquals(100L, result.getId());

        verify(alertRepository, never())
                .save(any(AlertEntity.class));
    }

    @Test
    void acknowledgeSos_shouldAcknowledgeCorrectEpisode() {

        when(alertRepository.findByDeviceIdAndSosEpisodeId(
                1L, "sos-test-001"
        )).thenReturn(Optional.of(alert));

        when(alertRepository.save(any(AlertEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<AlertEntity> result =
                alertService.acknowledgeSosAlert(
                        1L, "sos-test-001"
                );

        assertTrue(result.isPresent());
        assertEquals(
                AlertStatus.ACKNOWLEDGED,
                result.get().getStatus()
        );

        assertEquals(
                AlertEpisodeStatus.ACTIVE,
                result.get().getEpisodeStatus()
        );
    }

    @Test
    void endSos_shouldEndEpisodeWithoutResolvingAlert() {

        when(alertRepository.findByDeviceIdAndSosEpisodeId(
                1L, "sos-test-001"
        )).thenReturn(Optional.of(alert));

        when(alertRepository.save(any(AlertEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LocalDateTime endedAt = startedAt.plusMinutes(5);

        Optional<AlertEntity> result =
                alertService.endSosEpisode(
                        1L,
                        "sos-test-001",
                        endedAt
                );

        assertTrue(result.isPresent());
        assertEquals(
                AlertEpisodeStatus.ENDED,
                result.get().getEpisodeStatus()
        );

        assertEquals(
                AlertStatus.NEW,
                result.get().getStatus()
        );

        assertEquals(
                endedAt,
                result.get().getEndedAt()
        );
    }

    @Test
    void acknowledgeSos_shouldNotReopenEndedEpisode() {

        alert.setEpisodeStatus(AlertEpisodeStatus.ENDED);

        when(alertRepository.findByDeviceIdAndSosEpisodeId(
                1L, "sos-test-001"
        )).thenReturn(Optional.of(alert));

        Optional<AlertEntity> result =
                alertService.acknowledgeSosAlert(
                        1L, "sos-test-001"
                );

        assertTrue(result.isPresent());
        assertEquals(
                AlertEpisodeStatus.ENDED,
                result.get().getEpisodeStatus()
        );

        verify(alertRepository, never())
                .save(any(AlertEntity.class));
    }

    @Test
    void createSosAlert_shouldRejectBlankEpisodeId() {

        assertThrows(
                IllegalArgumentException.class,
                () -> alertService.createSosAlertIfAbsent(
                        1L,
                        " ",
                        "MANUAL_SOS",
                        startedAt
                )
        );

        verifyNoInteractions(alertRepository);
        verifyNoInteractions(deviceEventRepository);
    }


    @Test
    void createSosAlert_shouldCreateEndedEpisodeWhenEndArrivedFirst() {

        // ==================== ARRANGE ====================

        Long deviceId = 1L;
        String sosEpisodeId = "sos-end-before-start-001";
        String eventType = "MANUAL_SOS";

        // Thời điểm Backend nhận START đến trễ.
        LocalDateTime startedAt =
                LocalDateTime.of(2026, 11, 4, 9, 0);

        // Thời điểm Backend đã nhận END trước đó.
        LocalDateTime endReceivedAt =
                LocalDateTime.of(2026, 11, 4, 8, 59, 55);

        // Chưa tồn tại Alert của đợt SOS này.
        when(alertRepository.findByDeviceIdAndSosEpisodeId(
                deviceId,
                sosEpisodeId
        )).thenReturn(Optional.empty());

        // Tạo bản ghi sự kiện SOS_ENDED giả lập.
        DeviceEventEntity endEvent = new DeviceEventEntity();

        endEvent.setDeviceId(deviceId);
        endEvent.setSosEpisodeId(sosEpisodeId);
        endEvent.setEventType("SOS_ENDED");
        endEvent.setReceivedAt(endReceivedAt);

        // Giả lập Repository trả về sự kiện END đã lưu.
        when(deviceEventRepository
                .findFirstByDeviceIdAndSosEpisodeIdAndEventTypeOrderByReceivedAtAscIdAsc(
                        deviceId,
                        sosEpisodeId,
                        "SOS_ENDED"
                ))
                .thenReturn(Optional.of(endEvent));

        // Giả lập lưu Alert.
        when(alertRepository.save(any(AlertEntity.class)))
                .thenAnswer(invocation -> {

                    AlertEntity savedAlert =
                            invocation.getArgument(0);

                    savedAlert.setId(100L);

                    return savedAlert;
                });

        // ==================== ACT ====================

        AlertEntity result =
                alertService.createSosAlertIfAbsent(
                        deviceId,
                        sosEpisodeId,
                        eventType,
                        startedAt
                );

        // ==================== ASSERT ====================

        assertNotNull(result);

        assertEquals(100L, result.getId());
        assertEquals(deviceId, result.getDeviceId());
        assertEquals(sosEpisodeId, result.getSosEpisodeId());
        assertEquals(eventType, result.getAlertType());

        // Đợt SOS phải ở trạng thái ENDED.
        assertEquals(
                AlertEpisodeStatus.ENDED,
                result.getEpisodeStatus()
        );

        // Trạng thái xử lý nghiệp vụ vẫn là NEW.
        assertEquals(
                AlertStatus.NEW,
                result.getStatus()
        );

        // Kiểm tra thời điểm END đã lưu.
        assertNull(
                result.getEndedAt(),
                "END den truoc START: endedAt phai NULL khi chua xac dinh duoc thoi diem ket thuc"
        );

        // Đảm bảo START không bị tự ý thay đổi.
        assertEquals(
                startedAt,
                result.getStartedAt()
        );

        // Chỉ lưu một Alert.
        verify(alertRepository, times(1))
                .save(any(AlertEntity.class));

        // Xác nhận đã truy vấn đúng sự kiện END.
        verify(deviceEventRepository, times(1))
                .findFirstByDeviceIdAndSosEpisodeIdAndEventTypeOrderByReceivedAtAscIdAsc(
                        deviceId,
                        sosEpisodeId,
                        "SOS_ENDED"
                );
    }



    @Test
    void endSos_shouldEndEpisodeWhenEndReceivedAtIsEarlierThanStartedAt() {

        // Arrange
        Long deviceId = 1L;
        String sosEpisodeId = "sos-test-001";

        LocalDateTime startReceivedAt =
                LocalDateTime.of(2026, 11, 4, 9, 0, 35);

        LocalDateTime endReceivedAt =
                LocalDateTime.of(2026, 11, 4, 9, 0, 31);

        alert.setStartedAt(startReceivedAt);
        alert.setEpisodeStatus(AlertEpisodeStatus.ACTIVE);

        when(alertRepository.findByDeviceIdAndSosEpisodeId(
                deviceId,
                sosEpisodeId
        )).thenReturn(Optional.of(alert));

        when(alertRepository.save(any(AlertEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Optional<AlertEntity> result =
                alertService.endSosEpisode(
                        deviceId,
                        sosEpisodeId,
                        endReceivedAt
                );

        // Assert
        assertTrue(result.isPresent());

        assertEquals(
                AlertEpisodeStatus.ENDED,
                result.get().getEpisodeStatus()
        );

        // Chưa xác định được thời điểm kết thúc thực tế.
        assertNull(result.get().getEndedAt());

        verify(alertRepository, times(1))
                .save(any(AlertEntity.class));
    }

}

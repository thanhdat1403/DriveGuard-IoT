
package com.driveguard.backend.alert.service;

import com.driveguard.backend.alert.entity.AlertEntity;
import com.driveguard.backend.alert.enums.AlertEpisodeStatus;
import com.driveguard.backend.alert.enums.AlertStatus;
import com.driveguard.backend.alert.repository.AlertRepository;
import com.driveguard.backend.alert.service.Impl.AlertServiceImpl;
import com.driveguard.backend.deviceevent.entity.DeviceEventEntity;
import com.driveguard.backend.deviceevent.repository.DeviceEventRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SosEventSequenceTest {

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private DeviceEventRepository deviceEventRepository;

    private AlertServiceImpl alertService;

    private final Map<String, AlertEntity> alerts = new HashMap<>();

    private final Map<String, Boolean> ackEvents = new HashMap<>();
    private final Map<String, DeviceEventEntity> endEvents = new HashMap<>();

    private final Long deviceId = 1L;
    private final String episodeId = "sos-sequence-v9";

    private final LocalDateTime startTime =
            LocalDateTime.of(2026, 11, 10, 10, 0);

    private final LocalDateTime endTime =
            startTime.plusMinutes(5);

    private String key(Long device, String episode) {
        return device + ":" + episode;
    }

    @BeforeEach
    void setUp() {
        alertService = new AlertServiceImpl(
                alertRepository,
                deviceEventRepository
        );

        lenient().when(
                alertRepository.findByDeviceIdAndSosEpisodeId(
                        anyLong(), anyString()
                )
        ).thenAnswer(invocation -> {
            Long device = invocation.getArgument(0);
            String episode = invocation.getArgument(1);
            return Optional.ofNullable(
                    alerts.get(key(device, episode))
            );
        });

        lenient().when(
                alertRepository.save(any(AlertEntity.class))
        ).thenAnswer(invocation -> {
            AlertEntity entity = invocation.getArgument(0);
            alerts.put(
                    key(entity.getDeviceId(), entity.getSosEpisodeId()),
                    entity
            );
            return entity;
        });

        lenient().when(
                deviceEventRepository
                        .existsByDeviceIdAndSosEpisodeIdAndEventType(
                                anyLong(), anyString(), eq("SOS_ACKNOWLEDGED")
                        )
        ).thenAnswer(invocation -> {
            Long device = invocation.getArgument(0);
            String episode = invocation.getArgument(1);
            return ackEvents.getOrDefault(
                    key(device, episode), false
            );
        });

        lenient().when(
                deviceEventRepository
                        .findFirstByDeviceIdAndSosEpisodeIdAndEventTypeOrderByReceivedAtAscIdAsc(
                                anyLong(), anyString(), eq("SOS_ENDED")
                        )
        ).thenAnswer(invocation -> {
            Long device = invocation.getArgument(0);
            String episode = invocation.getArgument(1);
            return Optional.ofNullable(
                    endEvents.get(key(device, episode))
            );
        });
    }

    private AlertEntity start() {
        return alertService.createSosAlertIfAbsent(
                deviceId,
                episodeId,
                "MANUAL_SOS",
                startTime
        );
    }

    private void ack() {
        ackEvents.put(key(deviceId, episodeId), true);
        alertService.acknowledgeSosAlert(deviceId, episodeId);
    }

    private void end() {
        DeviceEventEntity event = new DeviceEventEntity();
        event.setDeviceId(deviceId);
        event.setSosEpisodeId(episodeId);
        event.setEventType("SOS_ENDED");
        event.setReceivedAt(endTime);

        endEvents.put(key(deviceId, episodeId), event);

        alertService.endSosEpisode(
                deviceId,
                episodeId,
                endTime
        );
    }

    private void assertFinalState() {
        AlertEntity result = alerts.get(key(deviceId, episodeId));

        assertNotNull(result);
        assertEquals(
                AlertEpisodeStatus.ENDED,
                result.getEpisodeStatus()
        );
        assertEquals(
                AlertStatus.ACKNOWLEDGED,
                result.getStatus()
        );
    }

    @Test
    void startAckEnd() {
        start();
        ack();
        end();
        assertFinalState();
    }

    @Test
    void startEndAck() {
        start();
        end();
        ack();
        assertFinalState();
    }

    @Test
    void ackStartEnd() {
        ack();
        start();
        end();
        assertFinalState();
    }

    @Test
    void endAckStart() {
        end();
        ack();
        start();
        assertFinalState();
        assertNull(
                alerts.get(key(deviceId, episodeId)).getEndedAt()
        );
    }

    @Test
    void duplicateStartMustNotReopenEndedEpisode() {
        start();
        end();

        AlertEntity result = start();

        assertEquals(
                AlertEpisodeStatus.ENDED,
                result.getEpisodeStatus()
        );
        verify(alertRepository, times(2))
                .save(any(AlertEntity.class));
    }

    @Test
    void duplicateAckAndEndMustBeIdempotent() {
        start();
        ack();
        ack();
        end();
        end();

        assertFinalState();

        verify(alertRepository, times(3))
                .save(any(AlertEntity.class));
    }
}

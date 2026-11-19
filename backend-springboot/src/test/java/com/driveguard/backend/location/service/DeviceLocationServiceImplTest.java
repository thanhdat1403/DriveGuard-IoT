package com.driveguard.backend.location.service;

import com.driveguard.backend.device.entity.DeviceEntity;
import com.driveguard.backend.device.repository.DeviceRepository;
import com.driveguard.backend.location.entity.DeviceLocationEntity;
import com.driveguard.backend.location.repository.DeviceLocationRepository;
import com.driveguard.backend.location.service.impl.DeviceLocationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DeviceLocationServiceImplTest {

    private DeviceRepository deviceRepository;
    private DeviceLocationRepository locationRepository;
    private DeviceLocationServiceImpl service;

    private static final String DEVICE_CODE = "ESP32S3-E2E-001";

    @BeforeEach
    void setUp() {
        deviceRepository = mock(DeviceRepository.class);
        locationRepository = mock(DeviceLocationRepository.class);

        service = new DeviceLocationServiceImpl(
                deviceRepository,
                locationRepository
        );
    }

    private DeviceLocationEntity save(
            String messageId,
            BigDecimal latitude,
            BigDecimal longitude,
            LocalDateTime measuredAt
    ) {
        return service.saveLocation(
                DEVICE_CODE,
                messageId,
                latitude,
                longitude,
                null,
                BigDecimal.valueOf(35),
                BigDecimal.valueOf(90),
                measuredAt
        );
    }

    @Test
    void validLocationShouldBeSaved() {
        DeviceEntity device = mock(DeviceEntity.class);

        when(deviceRepository.findByDeviceCode(DEVICE_CODE))
                .thenReturn(Optional.of(device));

        when(device.getId()).thenReturn(2L);

        when(locationRepository.findByDevice_IdAndMessageId(
                2L, "gps-test-001"
        )).thenReturn(Optional.empty());

        when(locationRepository.save(any(DeviceLocationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DeviceLocationEntity result = save(
                "gps-test-001",
                new BigDecimal("21.0285000"),
                new BigDecimal("105.8542000"),
                LocalDateTime.now(ZoneOffset.UTC).minusSeconds(10)
        );

        assertNotNull(result);
        assertEquals("gps-test-001", result.getMessageId());
        assertEquals(
                0,
                new BigDecimal("21.0285000")
                        .compareTo(result.getLatitude())
        );

        verify(locationRepository, times(1))
                .save(any(DeviceLocationEntity.class));
    }

    @Test
    void invalidLatitudeShouldBeRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> save(
                        "gps-test-002",
                        new BigDecimal("91"),
                        new BigDecimal("105.8542"),
                        LocalDateTime.now(ZoneOffset.UTC)
                )
        );

        verifyNoInteractions(deviceRepository, locationRepository);
    }

    @Test
    void invalidLongitudeShouldBeRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> save(
                        "gps-test-003",
                        new BigDecimal("21.0285"),
                        new BigDecimal("181"),
                        LocalDateTime.now(ZoneOffset.UTC)
                )
        );

        verifyNoInteractions(deviceRepository, locationRepository);
    }

    @Test
    void futureMeasuredAtShouldBeRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> save(
                        "gps-test-004",
                        new BigDecimal("21.0285"),
                        new BigDecimal("105.8542"),
                        LocalDateTime.now(ZoneOffset.UTC).plusHours(1)
                )
        );

        verifyNoInteractions(deviceRepository, locationRepository);
    }

    @Test
    void existingMessageShouldNotBeInsertedAgain() {
        DeviceEntity device = mock(DeviceEntity.class);

        when(deviceRepository.findByDeviceCode(DEVICE_CODE))
                .thenReturn(Optional.of(device));

        when(device.getId()).thenReturn(2L);

        LocalDateTime measuredAt =
                LocalDateTime.now(ZoneOffset.UTC).minusSeconds(10).truncatedTo(ChronoUnit.MILLIS);

        DeviceLocationEntity existing = new DeviceLocationEntity(
                device,
                "gps-test-005",
                new BigDecimal("21.0285"),
                new BigDecimal("105.8542"),
                null,
                BigDecimal.valueOf(35),
                BigDecimal.valueOf(90),
                measuredAt,
                LocalDateTime.now(ZoneOffset.UTC)
        );

        when(locationRepository.findByDevice_IdAndMessageId(
                2L, "gps-test-005"
        )).thenReturn(Optional.of(existing));

        DeviceLocationEntity result = save(
                "gps-test-005",
                new BigDecimal("21.0285"),
                new BigDecimal("105.8542"),
                measuredAt
        );

        assertSame(existing, result);
        verify(locationRepository, never())
                .save(any(DeviceLocationEntity.class));
    }

    @Test
    void unknownDeviceShouldBeRejected() {
        when(deviceRepository.findByDeviceCode(DEVICE_CODE))
                .thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> save(
                        "gps-test-006",
                        new BigDecimal("21.0285"),
                        new BigDecimal("105.8542"),
                        LocalDateTime.now(ZoneOffset.UTC)
                )
        );

        verify(locationRepository, never())
                .save(any(DeviceLocationEntity.class));
    }

    @Test
    void conflictingMessageShouldBeRejected() {
        DeviceEntity device = mock(DeviceEntity.class);

        when(deviceRepository.findByDeviceCode(DEVICE_CODE))
                .thenReturn(Optional.of(device));

        when(device.getId()).thenReturn(2L);

        LocalDateTime measuredAt =
                LocalDateTime.now(ZoneOffset.UTC).minusSeconds(10);

        DeviceLocationEntity existing = new DeviceLocationEntity(
                device,
                "gps-conflict-001",
                new BigDecimal("21.0285000"),
                new BigDecimal("105.8542000"),
                null,
                BigDecimal.valueOf(35),
                BigDecimal.valueOf(90),
                measuredAt,
                LocalDateTime.now(ZoneOffset.UTC)
        );

        when(locationRepository.findByDevice_IdAndMessageId(
                2L, "gps-conflict-001"
        )).thenReturn(Optional.of(existing));

        assertThrows(
                IllegalArgumentException.class,
                () -> save(
                        "gps-conflict-001",
                        new BigDecimal("21.0999000"),
                        new BigDecimal("105.8542000"),
                        measuredAt
                )
        );

        verify(locationRepository, never())
                .save(any(DeviceLocationEntity.class));
    }

    @Test
    void gpsTimestampShouldBeTruncatedToMilliseconds() {
        DeviceEntity device = mock(DeviceEntity.class);

        when(deviceRepository.findByDeviceCode(DEVICE_CODE))
                .thenReturn(Optional.of(device));

        when(device.getId()).thenReturn(2L);

        when(locationRepository.findByDevice_IdAndMessageId(
                2L, "gps-time-001"
        )).thenReturn(Optional.empty());

        when(locationRepository.save(any(DeviceLocationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LocalDateTime measuredAt = LocalDateTime.now(ZoneOffset.UTC)
                .minusSeconds(10)
                .withNano(123456789);

        DeviceLocationEntity result = save(
                "gps-time-001",
                new BigDecimal("21.0285000"),
                new BigDecimal("105.8542000"),
                measuredAt
        );

        assertEquals(
                measuredAt.truncatedTo(ChronoUnit.MILLIS),
                result.getMeasuredAt()
        );

        assertEquals(
                0,
                result.getReceivedAt().getNano() % 1_000_000
        );

        verify(locationRepository, times(1))
                .save(any(DeviceLocationEntity.class));
    }
}
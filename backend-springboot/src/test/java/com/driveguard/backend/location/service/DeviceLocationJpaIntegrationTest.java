package com.driveguard.backend.location.service;

import com.driveguard.backend.device.entity.DeviceEntity;
import com.driveguard.backend.device.repository.DeviceRepository;
import com.driveguard.backend.location.entity.DeviceLocationEntity;
import com.driveguard.backend.location.repository.DeviceLocationRepository;
import com.driveguard.backend.location.service.impl.DeviceLocationServiceImpl;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.test.database.replace=none"
})
@ActiveProfiles("e2e")
@Import(DeviceLocationServiceImpl.class)
class DeviceLocationJpaIntegrationTest {

    @Autowired
    private DeviceLocationServiceImpl service;

    @Autowired
    private DeviceLocationRepository locationRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String DEVICE_CODE = "ESP32S3-E2E-001";

    @BeforeEach
    void verifyDatabase() {
        String database = jdbcTemplate.queryForObject(
                "SELECT DATABASE()", String.class
        );

        assertEquals("driveguard_e2e", database);

        String port = jdbcTemplate.execute(
                (org.springframework.jdbc.core.ConnectionCallback<String>)
                        connection -> {
                            String url = connection.getMetaData().getURL();
                            assertTrue(
                                    url.contains(":3309/driveguard_e2e"),
                                    "Unexpected JDBC URL"
                            );
                            return url;
                        }
        );

        assertNotNull(port);
    }

    @Test
    void shouldPersistAndReloadGpsWithMillisecondPrecision() {
        DeviceEntity device = deviceRepository
                .findByDeviceCode(DEVICE_CODE)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "E2E GPS device does not exist"
                        )
                );

        String messageId = "gps-it-" + UUID.randomUUID();

        LocalDateTime measuredAt = LocalDateTime
                .now(ZoneOffset.UTC)
                .minusSeconds(10)
                .withNano(123456789);

        DeviceLocationEntity saved = service.saveLocation(
                DEVICE_CODE,
                messageId,
                new BigDecimal("21.0285000"),
                new BigDecimal("105.8542000"),
                new BigDecimal("5.50"),
                new BigDecimal("35.00"),
                new BigDecimal("90.00"),
                measuredAt
        );

        assertNotNull(saved.getId());

        entityManager.flush();
        entityManager.clear();

        DeviceLocationEntity reloaded = locationRepository
                .findByDevice_IdAndMessageId(
                        device.getId(), messageId
                )
                .orElseThrow();

        assertEquals(
                measuredAt.withNano(123000000),
                reloaded.getMeasuredAt()
        );

        assertEquals(
                0,
                new BigDecimal("21.0285000")
                        .compareTo(reloaded.getLatitude())
        );

        assertEquals(
                0,
                new BigDecimal("105.8542000")
                        .compareTo(reloaded.getLongitude())
        );

        assertEquals(
                0,
                reloaded.getReceivedAt().getNano() % 1_000_000
        );

        DeviceLocationEntity repeated = service.saveLocation(
                DEVICE_CODE,
                messageId,
                new BigDecimal("21.0285000"),
                new BigDecimal("105.8542000"),
                new BigDecimal("5.50"),
                new BigDecimal("35.00"),
                new BigDecimal("90.00"),
                measuredAt
        );

        assertEquals(reloaded.getId(), repeated.getId());

        assertEquals(
                1L,
                locationRepository
                        .findByDevice_IdAndMessageId(
                                device.getId(), messageId
                        )
                        .stream()
                        .count()
        );
    }
}

package com.driveguard.backend.deviceevent.integration;

import com.driveguard.backend.deviceevent.dto.mqtt.DeviceEventMessage;
import com.driveguard.backend.deviceevent.service.DeviceEventService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class DeviceEventConcurrencyIT {

    @Container
    static final MariaDBContainer mariaDB =
            new MariaDBContainer(
                    DockerImageName.parse("mariadb:12.3.2")
            )
                    .withDatabaseName("driveguard_test")
                    .withUsername("driveguard_test")
                    .withPassword("test_password")
                    .withCopyFileToContainer(
                            MountableFile.forClasspathResource(
                                    "db/schema-test.sql"
                            ),
                            "/docker-entrypoint-initdb.d/01-schema.sql"
                    );

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                mariaDB::getJdbcUrl
        );
        registry.add(
                "spring.datasource.username",
                mariaDB::getUsername
        );
        registry.add(
                "spring.datasource.password",
                mariaDB::getPassword
        );

        registry.add(
                "spring.jpa.hibernate.ddl-auto",
                () -> "validate"
        );
        registry.add(
                "driveguard.mqtt.enabled",
                () -> "false"
        );
        registry.add(
                "app.jwt.secret",
                () -> "driveguard-integration-test-only-secret-key-2026-not-for-production"
        );
    }

    @Autowired
    private DeviceEventService deviceEventService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void concurrentStartsSameEpisodeShouldCreateOneAlert()
            throws Exception {

        String deviceCode = "esp32s3-concurrency-001";
        String episodeId = "episode-concurrent-001";

        jdbcTemplate.update(
                """
                INSERT INTO devices
                    (device_code, device_name, device_type, status)
                VALUES (?, ?, ?, ?)
                """,
                deviceCode,
                "Concurrency Test Device",
                "ESP32",
                "ONLINE"
        );

        Long deviceId = jdbcTemplate.queryForObject(
                "SELECT id FROM devices WHERE device_code = ?",
                Long.class,
                deviceCode
        );

        assertNotNull(deviceId);

        DeviceEventMessage first = createMessage(
                deviceCode,
                "boot-test-001-1",
                "event-concurrent-001",
                episodeId,
                1L
        );

        DeviceEventMessage second = createMessage(
                deviceCode,
                "boot-test-001-2",
                "event-concurrent-002",
                episodeId,
                2L
        );

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> firstFuture = executor.submit(() -> {
                ready.countDown();
                awaitStart(start);

                deviceEventService.saveEvent(
                        first,
                        """
                        {"eventId":"event-concurrent-001","eventType":"MANUAL_SOS"}
                        """
                );
            });

            Future<?> secondFuture = executor.submit(() -> {
                ready.countDown();
                awaitStart(start);

                deviceEventService.saveEvent(
                        second,
                        """
                        {"eventId":"event-concurrent-002","eventType":"MANUAL_SOS"}
                        """
                );
            });

            assertTrue(
                    ready.await(10, TimeUnit.SECONDS),
                    "Both workers must be ready"
            );

            start.countDown();

            firstFuture.get(30, TimeUnit.SECONDS);
            secondFuture.get(30, TimeUnit.SECONDS);

        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        Long eventCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM device_events
                WHERE device_id = ?
                  AND sos_episode_id = ?
                """,
                Long.class,
                deviceId,
                episodeId
        );

        Long alertCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM alerts
                WHERE device_id = ?
                  AND sos_episode_id = ?
                """,
                Long.class,
                deviceId,
                episodeId
        );

        assertEquals(2L, eventCount);
        assertEquals(1L, alertCount);
    }

    private static DeviceEventMessage createMessage(
            String deviceCode,
            String bootId,
            String eventId,
            String episodeId,
            Long sequence
    ) {
        DeviceEventMessage message = new DeviceEventMessage();

        message.setDeviceId(deviceCode);
        message.setBootId(bootId);
        message.setEventId(eventId);
        message.setEventType("MANUAL_SOS");
        message.setSosEpisodeId(episodeId);
        message.setSequence(sequence);
        message.setTimestampMs(1000L + sequence);

        return message;
    }


    @Test
    void acknowledgeAfterEndShouldKeepEpisodeEnded() {

        String deviceCode = "esp32s3-ack-after-end-001";
        String episodeId = "episode-ack-after-end-001";
        String bootId = "boot-ack-after-end-001";

        // 1. Tạo thiết bị riêng cho bài kiểm thử.
        jdbcTemplate.update(
                """
                INSERT INTO devices
                    (device_code, device_name, device_type, status)
                VALUES (?, ?, ?, ?)
                """,
                deviceCode,
                "ACK After END Test Device",
                "ESP32",
                "ONLINE"
        );

        Long deviceId = jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM devices
                WHERE device_code = ?
                """,
                Long.class,
                deviceCode
        );

        assertNotNull(deviceId);

        // 2. START: bắt đầu episode.
        DeviceEventMessage startMessage = createMessage(
                deviceCode,
                bootId,
                "event-ack-after-end-start-001",
                episodeId,
                1L
        );

        startMessage.setEventType("MANUAL_SOS");
        startMessage.setTimestampMs(1000L);

        // 3. END: kết thúc episode.
        DeviceEventMessage endMessage = createMessage(
                deviceCode,
                bootId,
                "event-ack-after-end-end-001",
                episodeId,
                2L
        );

        endMessage.setEventType("SOS_ENDED");
        endMessage.setTimestampMs(2000L);

        // 4. ACK: xác nhận đến sau END.
        DeviceEventMessage ackMessage = createMessage(
                deviceCode,
                bootId,
                "event-ack-after-end-ack-001",
                episodeId,
                3L
        );

        ackMessage.setEventType("SOS_ACKNOWLEDGED");
        ackMessage.setTimestampMs(3000L);

        String startPayload = """
            {
              "eventId": "event-ack-after-end-start-001",
              "eventType": "MANUAL_SOS",
              "sosEpisodeId": "episode-ack-after-end-001"
            }
            """;

        String endPayload = """
            {
              "eventId": "event-ack-after-end-end-001",
              "eventType": "SOS_ENDED",
              "sosEpisodeId": "episode-ack-after-end-001"
            }
            """;

        String ackPayload = """
            {
              "eventId": "event-ack-after-end-ack-001",
              "eventType": "SOS_ACKNOWLEDGED",
              "sosEpisodeId": "episode-ack-after-end-001"
            }
            """;

        // 5. Thực hiện đúng thứ tự START -> END -> ACK.
        deviceEventService.saveEvent(
                startMessage,
                startPayload
        );

        deviceEventService.saveEvent(
                endMessage,
                endPayload
        );

        deviceEventService.saveEvent(
                ackMessage,
                ackPayload
        );

        // 6. Kiểm tra cả ba sự kiện được lưu.
        Long eventCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM device_events
                WHERE device_id = ?
                  AND sos_episode_id = ?
                """,
                Long.class,
                deviceId,
                episodeId
        );

        // 7. Kiểm tra chỉ có một alert.
        Long alertCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM alerts
                WHERE device_id = ?
                  AND sos_episode_id = ?
                """,
                Long.class,
                deviceId,
                episodeId
        );

        // 8. Kiểm tra trạng thái episode.
        String episodeStatus = jdbcTemplate.queryForObject(
                """
                SELECT episode_status
                FROM alerts
                WHERE device_id = ?
                  AND sos_episode_id = ?
                """,
                String.class,
                deviceId,
                episodeId
        );

        // 9. Kiểm tra trạng thái xác nhận.
        String alertStatus = jdbcTemplate.queryForObject(
                """
                SELECT status
                FROM alerts
                WHERE device_id = ?
                  AND sos_episode_id = ?
                """,
                String.class,
                deviceId,
                episodeId
        );

        assertEquals(
                3L,
                eventCount,
                "START, END and ACK must all be stored"
        );

        assertEquals(
                1L,
                alertCount,
                "Only one alert may exist"
        );

        assertEquals(
                "ENDED",
                episodeStatus,
                "ACK must not reopen an ended episode"
        );

        assertEquals(
                "ACKNOWLEDGED",
                alertStatus,
                "Late ACK must acknowledge the alert"
        );
    }


    @Test
    void endBeforeStartShouldRemainEnded() {

        String deviceCode = "esp32s3-end-before-start-001";
        String episodeId = "episode-end-before-start-001";

        // 1. Tạo thiết bị test.
        jdbcTemplate.update(
                """
                INSERT INTO devices
                    (device_code, device_name, device_type, status)
                VALUES (?, ?, ?, ?)
                """,
                deviceCode,
                "End Before Start Test Device",
                "ESP32",
                "ONLINE"
        );

        Long deviceId = jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM devices
                WHERE device_code = ?
                """,
                Long.class,
                deviceCode
        );

        assertNotNull(deviceId);

        // 2. Tạo END với sequence = 2.
        DeviceEventMessage endMessage = createMessage(
                deviceCode,
                "boot-end-before-start-001",
                "event-end-first-001",
                episodeId,
                2L
        );

        endMessage.setEventType("SOS_ENDED");
        endMessage.setTimestampMs(2000L);

        // 3. Tạo START với sequence = 1.
        DeviceEventMessage startMessage = createMessage(
                deviceCode,
                "boot-end-before-start-001",
                "event-start-late-001",
                episodeId,
                1L
        );

        startMessage.setEventType("MANUAL_SOS");
        startMessage.setTimestampMs(1000L);

        String endPayload = """
            {
              "eventId": "event-end-first-001",
              "eventType": "SOS_ENDED",
              "sosEpisodeId": "episode-end-before-start-001"
            }
            """;

        String startPayload = """
            {
              "eventId": "event-start-late-001",
              "eventType": "MANUAL_SOS",
              "sosEpisodeId": "episode-end-before-start-001"
            }
            """;

        // 4. Xử lý END trước, chờ transaction hoàn thành.
        deviceEventService.saveEvent(
                endMessage,
                endPayload
        );

        // 5. Xử lý START đến muộn.
        deviceEventService.saveEvent(
                startMessage,
                startPayload
        );

        // 6. Kiểm tra số sự kiện.
        Long eventCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM device_events
                WHERE device_id = ?
                  AND sos_episode_id = ?
                """,
                Long.class,
                deviceId,
                episodeId
        );

        // 7. Kiểm tra số alert.
        Long alertCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM alerts
                WHERE device_id = ?
                  AND sos_episode_id = ?
                """,
                Long.class,
                deviceId,
                episodeId
        );

        // 8. Kiểm tra trạng thái cuối cùng.
        String episodeStatus = jdbcTemplate.queryForObject(
                """
                SELECT episode_status
                FROM alerts
                WHERE device_id = ?
                  AND sos_episode_id = ?
                """,
                String.class,
                deviceId,
                episodeId
        );

        assertEquals(
                2L,
                eventCount,
                "Both END and late START must be stored"
        );

        assertEquals(
                1L,
                alertCount,
                "Only one alert may exist"
        );

        assertEquals(
                "ENDED",
                episodeStatus,
                "Late START must not reopen an ended episode"
        );
    }


    @Test
    void concurrentStartAndEndShouldFinishEpisode()
            throws Exception {

        String deviceCode = "esp32s3-start-end-001";
        String episodeId = "episode-start-end-001";

        // 1. Tạo thiết bị trong MariaDB Testcontainers.
        jdbcTemplate.update(
                """
                INSERT INTO devices
                    (device_code, device_name, device_type, status)
                VALUES (?, ?, ?, ?)
                """,
                deviceCode,
                "Start End Concurrency Device",
                "ESP32",
                "ONLINE"
        );

        Long deviceId = jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM devices
                WHERE device_code = ?
                """,
                Long.class,
                deviceCode
        );

        assertNotNull(deviceId);

        // 2. Sự kiện bắt đầu SOS.
        DeviceEventMessage startMessage = createMessage(
                deviceCode,
                "boot-start-end-001",
                "event-start-end-start-001",
                episodeId,
                1L
        );

        // 3. Sự kiện kết thúc cùng episode.
        DeviceEventMessage endMessage = createMessage(
                deviceCode,
                "boot-start-end-001",
                "event-start-end-end-001",
                episodeId,
                2L
        );

        endMessage.setEventType("SOS_ENDED");
        endMessage.setTimestampMs(2000L);

        String startPayload = """
            {
              "eventId": "event-start-end-start-001",
              "eventType": "MANUAL_SOS",
              "sosEpisodeId": "episode-start-end-001"
            }
            """;

        String endPayload = """
            {
              "eventId": "event-start-end-end-001",
              "eventType": "SOS_ENDED",
              "sosEpisodeId": "episode-start-end-001"
            }
            """;

        // 4. Chuẩn bị hai luồng.
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        try {
            Future<?> startFuture = executor.submit(() -> {
                ready.countDown();
                awaitStart(start);

                deviceEventService.saveEvent(
                        startMessage,
                        startPayload
                );
            });

            Future<?> endFuture = executor.submit(() -> {
                ready.countDown();
                awaitStart(start);

                deviceEventService.saveEvent(
                        endMessage,
                        endPayload
                );
            });

            assertTrue(
                    ready.await(10, TimeUnit.SECONDS),
                    "Both workers must be ready"
            );

            start.countDown();

            // Cả hai transaction phải hoàn thành.
            startFuture.get(30, TimeUnit.SECONDS);
            endFuture.get(30, TimeUnit.SECONDS);

        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        // 5. Kiểm tra cả hai event được lưu.
        Long eventCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM device_events
                WHERE device_id = ?
                  AND sos_episode_id = ?
                """,
                Long.class,
                deviceId,
                episodeId
        );

        // 6. Kiểm tra chỉ tồn tại một alert.
        Long alertCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM alerts
                WHERE device_id = ?
                  AND sos_episode_id = ?
                """,
                Long.class,
                deviceId,
                episodeId
        );

        // 7. Kiểm tra trạng thái cuối cùng.
        String episodeStatus = jdbcTemplate.queryForObject(
                """
                SELECT episode_status
                FROM alerts
                WHERE device_id = ?
                  AND sos_episode_id = ?
                """,
                String.class,
                deviceId,
                episodeId
        );

        assertEquals(
                2L,
                eventCount,
                "START and END must both be stored"
        );

        assertEquals(
                1L,
                alertCount,
                "Only one SOS alert may exist"
        );

        assertEquals(
                "ENDED",
                episodeStatus,
                "SOS episode must finish in ENDED state"
        );
    }



    @Test
    void concurrentDuplicateEventIdShouldBeIdempotent()
            throws Exception {

        String deviceCode = "esp32s3-duplicate-event-001";
        String episodeId = "episode-duplicate-event-001";
        String eventId = "event-duplicate-001";

        // 1. Tạo thiết bị riêng cho bài kiểm thử.
        jdbcTemplate.update(
                """
                INSERT INTO devices
                    (device_code, device_name, device_type, status)
                VALUES (?, ?, ?, ?)
                """,
                deviceCode,
                "Duplicate Event Test Device",
                "ESP32",
                "ONLINE"
        );

        Long deviceId = jdbcTemplate.queryForObject(
                "SELECT id FROM devices WHERE device_code = ?",
                Long.class,
                deviceCode
        );

        assertNotNull(deviceId);

        // 2. Hai bản tin có cùng eventId và sosEpisodeId.
        DeviceEventMessage first = createMessage(
                deviceCode,
                "boot-duplicate-001",
                eventId,
                episodeId,
                1L
        );

        DeviceEventMessage second = createMessage(
                deviceCode,
                "boot-duplicate-001",
                eventId,
                episodeId,
                1L
        );

        String rawPayload = """
            {
              "eventId": "event-duplicate-001",
              "eventType": "MANUAL_SOS",
              "sosEpisodeId": "episode-duplicate-event-001"
            }
            """;

        // 3. Chuẩn bị hai luồng đồng thời.
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        try {
            Future<?> firstFuture = executor.submit(() -> {
                ready.countDown();
                awaitStart(start);

                deviceEventService.saveEvent(
                        first,
                        rawPayload
                );
            });

            Future<?> secondFuture = executor.submit(() -> {
                ready.countDown();
                awaitStart(start);

                deviceEventService.saveEvent(
                        second,
                        rawPayload
                );
            });

            assertTrue(
                    ready.await(10, TimeUnit.SECONDS),
                    "Both workers must be ready"
            );

            start.countDown();

            // Cả hai lần xử lý phải hoàn thành.
            firstFuture.get(30, TimeUnit.SECONDS);
            secondFuture.get(30, TimeUnit.SECONDS);

        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        // 4. Kiểm tra không lưu trùng eventId.
        Long eventCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM device_events
                WHERE event_id = ?
                """,
                Long.class,
                eventId
        );

        // 5. Kiểm tra không tạo trùng cảnh báo SOS.
        Long alertCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM alerts
                WHERE device_id = ?
                  AND sos_episode_id = ?
                """,
                Long.class,
                deviceId,
                episodeId
        );

        assertEquals(
                1L,
                eventCount,
                "Duplicate eventId must be stored only once"
        );

        assertEquals(
                1L,
                alertCount,
                "Duplicate MQTT event must create only one SOS alert"
        );
    }


    private static void awaitStart(CountDownLatch start) {
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Timed out waiting for concurrent start"
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Concurrency test interrupted",
                    exception
            );
        }
    }
}

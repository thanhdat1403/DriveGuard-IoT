
package com.driveguard.backend.location.mqtt;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class GpsTelemetryParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GpsTelemetryParser parser = new GpsTelemetryParser();

    // Chuyển chuỗi JSON thành JsonNode để kiểm thử parser.
    private JsonNode toJson(String json) {
        return objectMapper.readTree(json);
    }

    // Bản tin GPS mô phỏng hợp lệ dùng chung cho các test.
    private String validGpsJson() {
        return """
                {
                  "deviceId": "ESP32S3-E2E-001",
                  "messageId": "gps-sim-000001",
                  "latitude": 21.0285,
                  "longitude": 105.8542,
                  "accuracyM": 5.50,
                  "speedKmh": 35.00,
                  "headingDeg": 90.00,
                  "measuredAt": "2026-11-16T15:30:00.123Z"
                }
                """;
    }

    @Test
    void validGpsShouldBeParsed() {
        GpsTelemetryMessage gps = parser.parse(
                toJson(validGpsJson())
        ).orElseThrow();

        assertEquals("ESP32S3-E2E-001", gps.deviceCode());
        assertEquals("gps-sim-000001", gps.messageId());

        assertEquals(
                0,
                new BigDecimal("21.0285").compareTo(gps.latitude())
        );

        assertEquals(
                0,
                new BigDecimal("105.8542").compareTo(gps.longitude())
        );

        assertEquals(
                LocalDateTime.of(
                        2026, 11, 16, 15, 30, 0, 123000000
                ),
                gps.measuredAt()
        );
    }

    @Test
    void sensorOnlyShouldReturnEmpty() {
        JsonNode json = toJson("""
                {
                  "deviceId": "ESP32S3-E2E-001",
                  "heartRate": 78,
                  "spo2": 98
                }
                """);

        assertTrue(parser.parse(json).isEmpty());
    }

    @Test
    void statusOnlyShouldReturnEmpty() {
        JsonNode json = toJson("""
                {
                  "deviceId": "ESP32S3-E2E-001",
                  "status": "ONLINE"
                }
                """);

        assertTrue(parser.parse(json).isEmpty());
    }

    @Test
    void combinedGpsAndSensorShouldBeParsed() {
        String combined = validGpsJson().replace(
                "\"deviceId\":",
                "\"heartRate\": 80, \"spo2\": 98, \"deviceId\":"
        );

        assertTrue(parser.parse(toJson(combined)).isPresent());
    }

    @Test
    void missingMessageIdShouldBeRejected() {
        String invalid = validGpsJson().replace(
                "\"messageId\": \"gps-sim-000001\",",
                ""
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(toJson(invalid))
        );
    }

    @Test
    void missingLatitudeShouldBeRejected() {
        String invalid = validGpsJson().replace(
                "\"latitude\": 21.0285,",
                ""
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(toJson(invalid))
        );
    }

    @Test
    void invalidLatitudeShouldBeRejected() {
        String invalid = validGpsJson().replace(
                "21.0285",
                "91.0"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(toJson(invalid))
        );
    }

    @Test
    void invalidLongitudeShouldBeRejected() {
        String invalid = validGpsJson().replace(
                "105.8542",
                "181.0"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(toJson(invalid))
        );
    }

    @Test
    void numericStringShouldBeRejected() {
        String invalid = validGpsJson().replace(
                "\"latitude\": 21.0285",
                "\"latitude\": \"21.0285\""
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(toJson(invalid))
        );
    }

    @Test
    void missingUtcSuffixShouldBeRejected() {
        String invalid = validGpsJson().replace(
                "2026-11-16T15:30:00.123Z",
                "2026-11-16T15:30:00.123"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(toJson(invalid))
        );
    }

    @Test
    void negativeSpeedShouldBeRejected() {
        String invalid = validGpsJson().replace(
                "\"speedKmh\": 35.00",
                "\"speedKmh\": -1.00"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(toJson(invalid))
        );
    }

    @Test
    void heading360ShouldBeRejected() {
        String invalid = validGpsJson().replace(
                "\"headingDeg\": 90.00",
                "\"headingDeg\": 360.00"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(toJson(invalid))
        );
    }

    @Test
    void excessiveCoordinateScaleShouldBeRejected() {
        String invalid = validGpsJson().replace(
                "21.0285",
                "21.02850001"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(toJson(invalid))
        );
    }

    @Test
    void excessiveAccuracyScaleShouldBeRejected() {
        String invalid = validGpsJson().replace(
                "\"accuracyM\": 5.50",
                "\"accuracyM\": 5.123"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(toJson(invalid))
        );
    }

    @Test
    void partialGpsShouldBeRejected() {
        JsonNode json = toJson("""
                {
                  "deviceId": "ESP32S3-E2E-001",
                  "latitude": 21.0285
                }
                """);

        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(json)
        );
    }

    @Test
    void optionalGpsFieldsMayBeAbsent() {
        JsonNode json = toJson("""
                {
                  "deviceId": "ESP32S3-E2E-001",
                  "messageId": "gps-minimal-001",
                  "latitude": 21.0285,
                  "longitude": 105.8542,
                  "measuredAt": "2026-11-16T15:30:00Z"
                }
                """);

        GpsTelemetryMessage gps =
                parser.parse(json).orElseThrow();

        assertNull(gps.accuracyM());
        assertNull(gps.speedKmh());
        assertNull(gps.headingDeg());
    }

    @Test
    void invalidJsonRootShouldBeRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(toJson("[]"))
        );
    }

    @Test
    void missingDeviceIdShouldBeRejected() {
        String invalid = validGpsJson().replace(
                "\"deviceId\": \"ESP32S3-E2E-001\",",
                ""
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(toJson(invalid))
        );
    }

    @Test
    void messageIdOver100CharactersShouldBeRejected() {
        String invalid = validGpsJson().replace(
                "gps-sim-000001",
                "x".repeat(101)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(toJson(invalid))
        );
    }
}

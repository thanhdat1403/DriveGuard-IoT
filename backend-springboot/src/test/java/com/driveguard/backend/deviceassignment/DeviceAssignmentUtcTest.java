
package com.driveguard.backend.deviceassignment;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeviceAssignmentUtcTest {

    @Test
    void shouldConvertVietnamTimeToUtc() {

        OffsetDateTime input =
                OffsetDateTime.parse(
                        "2026-11-06T14:40:00+07:00"
                );

        LocalDateTime utc =
                input.withOffsetSameInstant(ZoneOffset.UTC)
                        .toLocalDateTime();

        assertEquals(
                LocalDateTime.of(2026, 11, 6, 7, 40),
                utc
        );
    }

    @Test
    void shouldReturnUtcOffsetInApiResponse() {

        LocalDateTime storedUtc =
                LocalDateTime.of(2026, 11, 6, 7, 40);

        OffsetDateTime response =
                storedUtc.atOffset(ZoneOffset.UTC);

        assertEquals(
                "2026-11-06T07:40Z",
                response.toString()
        );
    }
}

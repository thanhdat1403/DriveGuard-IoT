
package com.driveguard.backend.deviceevent.controller;

import com.driveguard.backend.deviceevent.dto.response.DeviceEventResponse;
import com.driveguard.backend.deviceevent.service.DeviceEventQueryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/device-events")
public class DeviceEventController {

    private final DeviceEventQueryService queryService;

    // Lấy lịch sử sự kiện của một thiết bị, có hỗ trợ lọc eventType.
    @GetMapping("/devices/{deviceId}")
    public ResponseEntity<Page<DeviceEventResponse>> getDeviceEvents(
            @PathVariable Long deviceId,
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info(
                "[DEVICE-EVENT API] History | deviceId={} | type={} | page={} | size={}",
                deviceId, eventType, page, size
        );

        Page<DeviceEventResponse> response =
                queryService.getDeviceEvents(
                        deviceId,
                        eventType,
                        page,
                        size
                );

        return ResponseEntity.ok(response);
    }

    // Lấy các sự kiện thuộc một đợt SOS, theo thứ tự nhận.
    @GetMapping("/devices/{deviceId}/episodes/{sosEpisodeId}")
    public ResponseEntity<Page<DeviceEventResponse>> getSosEpisodeEvents(
            @PathVariable Long deviceId,
            @PathVariable String sosEpisodeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info(
                "[DEVICE-EVENT API] SOS episode | deviceId={} | episodeId={}",
                deviceId, sosEpisodeId
        );

        Page<DeviceEventResponse> response =
                queryService.getSosEpisodeEvents(
                        deviceId,
                        sosEpisodeId,
                        page,
                        size
                );

        return ResponseEntity.ok(response);
    }

    // Tra cứu chi tiết bằng eventId do ESP32-S3 gửi qua MQTT.
    @GetMapping("/{eventId}")
    public ResponseEntity<DeviceEventResponse> getEventByEventId(
            @PathVariable String eventId
    ) {
        log.info(
                "[DEVICE-EVENT API] Detail | eventId={}",
                eventId
        );

        DeviceEventResponse response =
                queryService.getEventByEventId(eventId);

        return ResponseEntity.ok(response);
    }
}

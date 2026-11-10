package com.driveguard.backend.deviceassignment.controller;

import com.driveguard.backend.deviceassignment.dto.request.DeviceAssignmentCreateRequest;
import com.driveguard.backend.deviceassignment.dto.request.DeviceAssignmentEndRequest;
import com.driveguard.backend.deviceassignment.dto.response.DeviceAssignmentResponse;
import com.driveguard.backend.deviceassignment.entity.DeviceAssignmentEntity;
import com.driveguard.backend.deviceassignment.service.DeviceAssignmentService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@RestController // Đánh dấu lớp này là một REST Controller xử lý API trả về JSON
@RequestMapping("/api/v1/device-assignments") // Cấu hình đường dẫn gốc cho toàn bộ API trong lớp này
@RequiredArgsConstructor // Tự động tạo Constructor để tự nhúng (Inject) Service vào
@Validated // Bật tính năng kiểm tra tính hợp lệ dữ liệu (Validation) cho các tham số URL như @PathVariable
public class DeviceAssignmentController {

    private final DeviceAssignmentService assignmentService; // Tiêm tầng Service xử lý nghiệp vụ

    private final JdbcTemplate jdbcTemplate;

    /**
     * API: Tạo một lượt phân công thiết bị mới cho tài xế.
     * Phương thức: POST | Đường dẫn: /api/v1/device-assignments
     */

    @PostMapping
    public ResponseEntity<DeviceAssignmentResponse> createAssignment(
            @Valid @RequestBody DeviceAssignmentCreateRequest request
    ) {

        // Chuyển thời gian bắt đầu sang UTC
        LocalDateTime startedAtUtc =
                toUtcLocalDateTime(request.getStartedAt());

        // endedAt có thể null nếu phân công chưa kết thúc
        LocalDateTime endedAtUtc =
                toUtcLocalDateTime(request.getEndedAt());

        // Gọi Service với thời gian đã chuẩn hóa UTC
        DeviceAssignmentEntity assignment =
                assignmentService.assignDevice(
                        request.getDeviceId(),
                        request.getDriverId(),
                        startedAtUtc,
                        endedAtUtc
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(DeviceAssignmentResponse.fromEntity(assignment));
    }


    /**
     * API: Kết thúc một lượt phân công đang diễn ra (Bàn giao lại thiết bị).
     * Phương thức: PATCH | Đường dẫn: /api/v1/device-assignments/{assignmentId}/end
     */
    @PatchMapping("/{assignmentId}/end") // Ý nghĩa hành động: Chỉnh sửa một phần
    public ResponseEntity<DeviceAssignmentResponse> endAssignment(
            @PathVariable @Positive Long assignmentId,
            @Valid @RequestBody DeviceAssignmentEndRequest request
    ) {

        // Chuyển thời gian kết thúc về UTC
        LocalDateTime endedAtUtc =
                toUtcLocalDateTime(request.getEndedAt());

        DeviceAssignmentEntity assignment =
                assignmentService.endAssignment(
                        assignmentId,
                        endedAtUtc
                );

        return ResponseEntity.ok(
                DeviceAssignmentResponse.fromEntity(assignment)
        );
    }

    /**
     * API: Lấy thông tin lượt phân công chưa kết thúc của một Thiết bị cụ thể.
     * Phương thức: GET | Đường dẫn: /api/v1/device-assignments/devices/{deviceId}/open
     */
    @GetMapping("/devices/{deviceId}/open")
    public ResponseEntity<DeviceAssignmentResponse>
    getOpenAssignmentByDevice(
            @PathVariable @Positive Long deviceId // Lấy ID thiết bị từ URL
    ){
        return assignmentService
                .getActiveAssignmentByDevice(deviceId) // Gọi Service tìm kiếm lượt phân công mở
                .map(DeviceAssignmentResponse::fromEntity) // Nếu tìm thấy, chuyển sang DTO
                .map(ResponseEntity::ok) // Trả về mã HTTP 200 (OK) kèm dữ liệu
                .orElseGet(() ->
                        ResponseEntity.notFound().build() // Nếu không tìm thấy, trả về mã HTTP 404 (Not Found)
                );

    }

    /**
     * API: Lấy thông tin lượt phân công chưa kết thúc của một Tài xế cụ thể.
     * Phương thức: GET | Đường dẫn: /api/v1/device-assignments/drivers/{driverId}/open
     */
    @GetMapping("/drivers/{driverId}/open")
    public ResponseEntity<DeviceAssignmentResponse>
    getOpenAssignmentByDriver(
            @PathVariable @Positive Long driverId // Lấy ID tài xế từ URL
    ) {
        return assignmentService
                .getActiveAssignmentByDriver(driverId) // Gọi Service tìm kiếm lượt phân công mở theo tài xế
                .map(DeviceAssignmentResponse::fromEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() ->
                        ResponseEntity.notFound().build() // Trả về 404 nếu tài xế hiện chưa được giao thiết bị nào
                );
    }

    /**
     * API: Xem lại toàn bộ lịch sử phân công của một Thiết bị.
     * Phương thức: GET | Đường dẫn: /api/v1/device-assignments/devices/{deviceId}/history
     */

    @GetMapping("/devices/{deviceId}/history")
    public ResponseEntity<List<DeviceAssignmentResponse>> getDeviceHistory(
            @PathVariable @Positive Long deviceId
    ) {

        List<DeviceAssignmentEntity> assignments =
                assignmentService.getDeviceAssignmentHistory(deviceId);

        // DEBUG: Kiểm tra thời gian Hibernate đọc từ MariaDB
        for (DeviceAssignmentEntity assignment : assignments) {
            System.out.println(
                    "[UTC DEBUG] ID=" + assignment.getId()
                            + " | startedAt=" + assignment.getStartedAt()
                            + " | endedAt=" + assignment.getEndedAt()
                            + " | createdAt=" + assignment.getCreatedAt()
                            + " | updatedAt=" + assignment.getUpdatedAt()
            );
        }

        List<DeviceAssignmentResponse> responses =
                assignments.stream()
                        .map(DeviceAssignmentResponse::fromEntity)
                        .toList();

        return ResponseEntity.ok(responses);
    }


    /**
     * API: Xem lại toàn bộ lịch sử phân công của một Tài xế.
     * Phương thức: GET | Đường dẫn: /api/v1/device-assignments/drivers/{driverId}/history
     */
    @GetMapping("/drivers/{driverId}/history")
    public ResponseEntity<List<DeviceAssignmentResponse>>
    getDriverHistory(
            @PathVariable @Positive Long driverId
    ) {
        // Gọi Service lấy danh sách lịch sử theo tài xế, chuyển đổi sang DTO và trả về List
        List<DeviceAssignmentResponse> responses =
                assignmentService
                        .getDriverAssignmentHistory(driverId)
                        .stream()
                        .map(DeviceAssignmentResponse::fromEntity)
                        .toList();

        return ResponseEntity.ok(responses); // Trả về danh sách lịch sử kèm HTTP 200
    }

    /**
     * Lấy phân công đang có hiệu lực của thiết bị
     * tại thời điểm hiện tại.
     *
     * GET /api/v1/device-assignments/devices/{deviceId}/active
     */
    @GetMapping("/devices/{deviceId}/active")
    public ResponseEntity<DeviceAssignmentResponse>
    getActiveAssignmentByDevice(
            @PathVariable @Positive Long deviceId
    ){
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        return assignmentService
                .getActiveAssignmentByDeviceAtTime(deviceId, now)
                .map(DeviceAssignmentResponse::fromEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() ->
                        ResponseEntity.notFound().build()
                );
    }

    /**
     * Lấy phân công đang có hiệu lực của tài xế
     * tại thời điểm hiện tại.
     *
     * GET /api/v1/device-assignments/drivers/{driverId}/active
     */
    @GetMapping("/drivers/{driverId}/active")
    public ResponseEntity<DeviceAssignmentResponse>
    getActiveAssignmentByDriver(
            @PathVariable @Positive Long driverId
    ) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        return assignmentService
                .getActiveAssignmentByDriverAtTime(driverId, now)
                .map(DeviceAssignmentResponse::fromEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() ->
                        ResponseEntity.notFound().build()
                );
    }

    /**
     * Chuyển OffsetDateTime sang LocalDateTime theo UTC.
     *
     * Ví dụ:
     * 2026-11-06T14:40:00+07:00
     * -> 2026-11-06T07:40:00
     */
    private LocalDateTime toUtcLocalDateTime(
            OffsetDateTime dateTime
    ) {
        if (dateTime == null) {
            return null;
        }
        return dateTime
                .withOffsetSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }


    @GetMapping("/debug/database-time")
    public ResponseEntity<Map<String, Object>> debugDatabaseTime() {

        Map<String, Object> result = jdbcTemplate.execute(
                (org.springframework.jdbc.core.ConnectionCallback<Map<String, Object>>) connection -> {

                    Map<String, Object> data = new java.util.LinkedHashMap<>();

                    try (var statement = connection.createStatement()) {

                        try (var rs = statement.executeQuery("""
                            SELECT
                                CONNECTION_ID(),
                                @@hostname,
                                DATABASE(),
                                @@autocommit,
                                @@in_transaction
                            """)) {

                            if (rs.next()) {
                                data.put("connectionId", rs.getLong(1));
                                data.put("databaseHost", rs.getString(2));
                                data.put("databaseName", rs.getString(3));
                                data.put("autocommit", rs.getInt(4));
                                data.put("inTransaction", rs.getInt(5));
                            }
                        }

                        try (var rs = statement.executeQuery("""
                            SELECT
                                DATE_FORMAT(started_at, '%Y-%m-%d %H:%i:%s'),
                                DATE_FORMAT(ended_at, '%Y-%m-%d %H:%i:%s'),
                                DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s')
                            FROM device_assignments
                            WHERE id = 3
                            """)) {

                            if (rs.next()) {
                                data.put("startedAt", rs.getString(1));
                                data.put("endedAt", rs.getString(2));
                                data.put("updatedAt", rs.getString(3));
                            }
                        }
                    }

                    return data;
                }
        );

        return ResponseEntity.ok(result);
    }



}

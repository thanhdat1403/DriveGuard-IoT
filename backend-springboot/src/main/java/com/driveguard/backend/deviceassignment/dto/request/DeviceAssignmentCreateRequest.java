
package com.driveguard.backend.deviceassignment.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
public class DeviceAssignmentCreateRequest {

    // ID thiết bị ESP32-S3
    @NotNull(message = "deviceId không được để trống")
    @Positive(message = "deviceId phải lớn hơn 0")
    private Long deviceId;

    // ID tài xế
    @NotNull(message = "driverId không được để trống")
    @Positive(message = "driverId phải lớn hơn 0")
    private Long driverId;

    // Thời điểm bắt đầu, bắt buộc có UTC offset
    @NotNull(message = "startedAt không được để trống")
    private OffsetDateTime startedAt;

    // Có thể null nếu chưa xác định thời điểm kết thúc
    private OffsetDateTime endedAt;
}

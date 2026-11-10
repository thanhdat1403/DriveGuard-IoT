
package com.driveguard.backend.deviceassignment.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
public class DeviceAssignmentEndRequest {

    // Thời điểm kết thúc thực tế, bắt buộc có UTC offset
    @NotNull(message = "endedAt không được để trống")
    private OffsetDateTime endedAt;
}


package com.driveguard.backend.deviceassignment.entity;

import com.driveguard.backend.device.entity.DeviceEntity;
import com.driveguard.backend.driver.entity.DriverEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "device_assignments")
@Getter
@Setter
@NoArgsConstructor
public class DeviceAssignmentEntity {

    /**
     * Khóa chính của lần phân công thiết bị.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /**
     * Thiết bị được phân công.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private DeviceEntity device;

    /**
     * Tài xế nhận thiết bị.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false)
    private DriverEntity driver;

    /**
     * Thời điểm bắt đầu phiên phân công.
     */
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    /**
     * NULL nghĩa là phiên phân công chưa kết thúc.
     */
    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    /**
     * Thời điểm tạo bản ghi.
     * Do database quản lý.
     */
    @Column(
            name = "created_at",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    /**
     * Thời điểm cập nhật.
     * Do database quản lý.
     */
    @Column(
            name = "updated_at",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private LocalDateTime updatedAt;
}

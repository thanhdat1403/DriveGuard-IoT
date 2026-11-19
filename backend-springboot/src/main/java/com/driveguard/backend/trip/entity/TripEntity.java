
package com.driveguard.backend.trip.entity;

import com.driveguard.backend.device.entity.DeviceEntity;
import com.driveguard.backend.deviceassignment.entity.DeviceAssignmentEntity;
import com.driveguard.backend.driver.entity.DriverEntity;
import com.driveguard.backend.trip.enums.TripStatus;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "trips")
@Getter
@Setter
@NoArgsConstructor
public class TripEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private DeviceAssignmentEntity assignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private DeviceEntity device;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false)
    private DriverEntity driver;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TripStatus status;

    // Các thời điểm được lưu theo UTC.
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    // Cột sinh tự động, chỉ đọc từ database.
    @Column(
            name = "active_device_id",
            insertable = false,
            updatable = false
    )
    private Long activeDeviceId;

    @Column(
            name = "active_driver_id",
            insertable = false,
            updatable = false
    )
    private Long activeDriverId;

    @Column(
            name = "created_at",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    @Column(
            name = "updated_at",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private LocalDateTime updatedAt;
}

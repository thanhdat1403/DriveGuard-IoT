
package com.driveguard.backend.driver.entity;

import com.driveguard.backend.user.entity.UserEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "drivers")
@Getter
@Setter
@NoArgsConstructor
public class DriverEntity {

    /**
     * Khóa chính của hồ sơ tài xế.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /**
     * Liên kết 1-1 với tài khoản đăng nhập.
     *
     * Database đã có:
     * - FK: fk_drivers_user
     * - UNIQUE: uk_drivers_user_id
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            unique = true
    )
    private UserEntity user;

    /**
     * Mã tài xế duy nhất.
     * Khớp với VARCHAR(30) trong MariaDB.
     */
    @Column(
            name = "driver_code",
            nullable = false,
            unique = true,
            length = 30
    )
    private String driverCode;

    /**
     * Số điện thoại của tài xế.
     */
    @Column(name = "phone", length = 20)
    private String phone;

    /**
     * Số giấy phép lái xe.
     *
     * Cho phép NULL.
     * Database đã có UNIQUE constraint.
     */
    @Column(
            name = "license_number",
            unique = true,
            length = 50
    )
    private String licenseNumber;

    /**
     * Thời gian tạo do MariaDB quản lý.
     */
    @Column(
            name = "created_at",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    /**
     * Thời gian cập nhật do MariaDB quản lý.
     */
    @Column(
            name = "updated_at",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private LocalDateTime updatedAt;
}

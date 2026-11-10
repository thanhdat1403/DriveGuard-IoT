
package com.driveguard.backend.driver.repository;

import com.driveguard.backend.driver.entity.DriverEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DriverRepository
        extends JpaRepository<DriverEntity, Long> {

    /**
     * Tìm hồ sơ tài xế theo ID tài khoản User.
     *
     * Dùng để xác định tài xế đang đăng nhập
     * trước khi truy vấn dữ liệu Dashboard.
     */
    Optional<DriverEntity> findByUser_Id(Long userId);

    /**
     * Tìm hồ sơ tài xế theo username.
     *
     * Có thể sử dụng khi JWT cung cấp username
     * làm định danh người dùng.
     */
    Optional<DriverEntity> findByUser_Username(String username);

    /**
     * Tìm tài xế theo mã tài xế.
     */
    Optional<DriverEntity> findByDriverCode(String driverCode);

    /**
     * Kiểm tra một tài khoản đã có
     * hồ sơ tài xế hay chưa.
     */
    boolean existsByUser_Id(Long userId);

    /**
     * Kiểm tra mã tài xế đã tồn tại.
     */
    boolean existsByDriverCode(String driverCode);

    /**
     * Kiểm tra số giấy phép lái xe đã tồn tại.
     *
     * Chỉ sử dụng khi licenseNumber khác null.
     */
    boolean existsByLicenseNumber(String licenseNumber);


    /**
     * Khóa hồ sơ tài xế trong transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    SELECT d
    FROM DriverEntity d
    WHERE d.id = :driverId
    """)
    Optional<DriverEntity> findByIdForUpdate(
            @Param("driverId") Long driverId
    );
}

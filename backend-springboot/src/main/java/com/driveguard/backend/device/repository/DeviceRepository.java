
package com.driveguard.backend.device.repository;

import com.driveguard.backend.device.entity.DeviceEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DeviceRepository
        extends JpaRepository<DeviceEntity, Long> {

    Optional<DeviceEntity> findByDeviceCode(String deviceCode);

    /**
     * Khóa bản ghi thiết bị trong transaction.
     * Dùng để tuần tự hóa các yêu cầu phân công
     * liên quan đến cùng một thiết bị.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    SELECT d
    FROM DeviceEntity d
    WHERE d.id = :deviceId
    """)
    Optional<DeviceEntity> findByIdForUpdate(
            @Param("deviceId") Long deviceId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT d
        FROM DeviceEntity d
        WHERE d.deviceCode = :deviceCode
        """)
    Optional<DeviceEntity> findByDeviceCodeForUpdate(
            @Param("deviceCode") String deviceCode
    );
}

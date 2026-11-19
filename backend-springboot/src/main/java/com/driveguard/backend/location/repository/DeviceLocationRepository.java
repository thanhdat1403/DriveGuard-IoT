package com.driveguard.backend.location.repository;

import com.driveguard.backend.location.entity.DeviceLocationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DeviceLocationRepository
        extends JpaRepository<DeviceLocationEntity, Long> {

    // Chống lưu trùng bản tin GPS của cùng thiết bị.
    Optional<DeviceLocationEntity> findByDevice_IdAndMessageId(
            Long deviceId,
            String messageId
    );

    // Vị trí mới nhất theo thời điểm GPS đo, không theo lúc nhận.
    Optional<DeviceLocationEntity>
    findFirstByDevice_IdOrderByMeasuredAtDescIdDesc(
            Long deviceId
    );

    // Lịch sử GPS có phân trang.
    Page<DeviceLocationEntity>
    findByDevice_IdOrderByMeasuredAtDescIdDesc(
            Long deviceId,
            Pageable pageable
    );


    /**
     * Lấy tọa độ GPS của một Trip theo thời gian tăng dần.
     * Dùng để vẽ hành trình từ điểm bắt đầu đến điểm kết thúc.
     */
    Page<DeviceLocationEntity>
    findByTrip_IdOrderByMeasuredAtAscIdAsc(
            Long tripId,
            Pageable pageable
    );

    /**
     * Đếm số điểm GPS đã liên kết với một Trip.
     */
    long countByTrip_Id(Long tripId);

}

package com.driveguard.backend.sensor.repository;

import com.driveguard.backend.sensor.entity.SensorReadingEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository quản lý dữ liệu cảm biến trong bảng sensor_readings.
 *
 * JpaRepository cung cấp sẵn các phương thức:
 * - save(): Lưu dữ liệu cảm biến vào database.
 * - findById(): Tìm bản ghi theo ID.
 * - findAll(): Lấy danh sách bản ghi.
 * - deleteById(): Xóa bản ghi theo ID.
 *
 * Không cần tự viết câu SQL cho các thao tác CRUD cơ bản.
 */
@Repository
public interface SensorReadingRepository
        extends JpaRepository<SensorReadingEntity, Long> {

    /**
     * Lấy bản ghi cảm biến mới nhất của một thiết bị.
     *
     * Spring Data JPA tự tạo câu truy vấn dựa trên tên phương thức.
     *
     * device.id: Truy cập khóa chính của DeviceEntity.
     * OrderByMeasuredAtDesc: Sắp xếp theo thời gian đo giảm dần.
     * OrderByIdDesc: Ưu tiên ID lớn hơn nếu thời gian đo trùng nhau.
     *
     * Optional giúp xử lý trường hợp thiết bị chưa có dữ liệu.
     */
    Optional<SensorReadingEntity>
    findFirstByDevice_IdOrderByMeasuredAtDescIdDesc(Long deviceId);

    /**
     * Lấy lịch sử dữ liệu cảm biến theo thiết bị.
     *
     * Pageable hỗ trợ phân trang và giới hạn số lượng bản ghi.
     * Page chứa danh sách dữ liệu và thông tin tổng số trang.
     *
     * Sắp xếp dữ liệu mới nhất lên trước.
     */
    Page<SensorReadingEntity>
    findByDevice_IdOrderByMeasuredAtDescIdDesc(
            Long deviceId,
            Pageable pageable
    );


    /**
     * Đếm tổng số bản ghi cảm biến của một thiết bị.
     *
     * Dùng cho Dashboard Summary.
     *
     * Spring Data JPA tự sinh truy vấn COUNT.
     */
    long countByDevice_Id(Long deviceId);

}


package com.driveguard.backend.deviceassignment.service;

import com.driveguard.backend.deviceassignment.entity.DeviceAssignmentEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DeviceAssignmentService {

    /**
     * Phân công thiết bị cho tài xế.
     *
     * @param deviceId ID thiết bị
     * @param driverId ID hồ sơ tài xế
     * @param startedAt Thời điểm bắt đầu
     * @param endedAt Thời điểm kết thúc dự kiến,
     *                null nếu chưa xác định
     *
     * @return Phân công vừa tạo
     */
    DeviceAssignmentEntity assignDevice(
            Long deviceId,
            Long driverId,
            LocalDateTime startedAt,
            LocalDateTime endedAt
    );

    /**
     * Kết thúc một phân công đang hoạt động.
     *
     * @param assignmentId ID phân công
     * @param endedAt Thời điểm kết thúc thực tế
     */
    DeviceAssignmentEntity endAssignment(
            Long assignmentId,
            LocalDateTime endedAt
    );

    /**
     * Tìm phân công đang hoạt động của thiết bị.
     */
    Optional<DeviceAssignmentEntity>
    getActiveAssignmentByDevice(Long deviceId);

    /**
     * Tìm phân công đang hoạt động của tài xế.
     */
    Optional<DeviceAssignmentEntity>
    getActiveAssignmentByDriver(Long driverId);

    /**
     * Lấy lịch sử phân công của thiết bị.
     */
    List<DeviceAssignmentEntity>
    getDeviceAssignmentHistory(Long deviceId);

    /**
     * Lấy lịch sử phân công của tài xế.
     *
     * Lưu ý: khi xây dựng Controller,
     * phải kiểm tra quyền truy cập.
     */
    List<DeviceAssignmentEntity>
    getDriverAssignmentHistory(Long driverId);

    /**
     * Xác định phân công tại thời điểm đo.
     *
     * Chỉ trả về kết quả khi xác định được
     * duy nhất một phân công hợp lệ.
     *
     * Nếu không có phân công hoặc phát hiện
     * dữ liệu chồng lấn, không được tự ý
     * gán dữ liệu cho tài xế.
     */
    Optional<DeviceAssignmentEntity>
    findAssignmentAtTime(
            Long deviceId,
            LocalDateTime measuredAt
    );


    Optional<DeviceAssignmentEntity> getActiveAssignmentByDeviceAtTime(
            Long deviceId,
            LocalDateTime atTime
    );

    Optional<DeviceAssignmentEntity> getActiveAssignmentByDriverAtTime(
            Long driverId,
            LocalDateTime atTime
    );

}

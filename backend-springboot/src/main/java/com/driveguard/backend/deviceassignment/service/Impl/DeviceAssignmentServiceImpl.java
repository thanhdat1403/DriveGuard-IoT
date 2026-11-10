package com.driveguard.backend.deviceassignment.service.Impl;

import com.driveguard.backend.device.entity.DeviceEntity;
import com.driveguard.backend.device.repository.DeviceRepository;
import com.driveguard.backend.deviceassignment.entity.DeviceAssignmentEntity;
import com.driveguard.backend.deviceassignment.repository.DeviceAssignmentRepository;
import com.driveguard.backend.deviceassignment.service.DeviceAssignmentService;
import com.driveguard.backend.driver.entity.DriverEntity;
import com.driveguard.backend.driver.repository.DriverRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import lombok.RequiredArgsConstructor;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.ZoneOffset;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DeviceAssignmentServiceImpl
        implements DeviceAssignmentService {

    private final DeviceAssignmentRepository assignmentRepository;
    private final DeviceRepository deviceRepository;
    private final DriverRepository driverRepository;
    private final EntityManager entityManager;

    //Phân công thiết bị cho tài xế.
    @Override
    @Transactional
    public DeviceAssignmentEntity assignDevice(
            Long deviceId,
            Long driverId,
            LocalDateTime startedAt,
            LocalDateTime endedAt
    ) {

        requireId(deviceId, "deviceId");
        requireId(driverId, "driverId");
        validateTimeRange(startedAt, endedAt);

        // Khóa thiết bị trước, sau đó khóa tài xế.
        DeviceEntity device = deviceRepository
                .findByIdForUpdate(deviceId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Không tìm thấy thiết bị: " + deviceId
                        )
                );


        DriverEntity driver = driverRepository
                .findByIdForUpdate(driverId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Không tìm thấy tài xế: " + driverId
                        )
                );


        validateDevice(device);
        validateDriver(driver);

        long deviceOverlaps =
                assignmentRepository.countOverlappingDeviceAssignments(
                        deviceId,
                        startedAt,
                        endedAt
                );

        if (deviceOverlaps > 0) {
            throw new IllegalStateException(
                    "Thiết bị đã được phân công trong khoảng thời gian này"
            );
        }

        long driverOverlaps =
                assignmentRepository.countOverlappingDriverAssignments(
                        driverId,
                        startedAt,
                        endedAt
                );

        if (driverOverlaps > 0) {
            throw new IllegalStateException(
                    "Tài xế đã có phân công trong khoảng thời gian này"
            );
        }

        DeviceAssignmentEntity assignment =
                new DeviceAssignmentEntity();

        assignment.setDevice(device);
        assignment.setDriver(driver);
        assignment.setStartedAt(startedAt);
        assignment.setEndedAt(endedAt);

        DeviceAssignmentEntity savedAssignment =
                assignmentRepository.saveAndFlush(assignment); // yêu cầu Hibernate thực hiện SQL INSERT trước khi trả kết quả

        entityManager.refresh(savedAssignment); // Đọc dữ liệu từ MariaDB

        return savedAssignment;
    }

    //Kết thúc một phân công đang hoạt động.

    @Override
    @Transactional
    public DeviceAssignmentEntity endAssignment(
            Long assignmentId,
            LocalDateTime endedAt
    ) {
        requireId(assignmentId, "assignmentId");

        if (endedAt == null) {
            throw new IllegalArgumentException(
                    "Thời điểm kết thúc không được để trống"
            );
        }

        // Đọc thông tin để xác định thiết bị và tài xế.
        DeviceAssignmentEntity existing =
                assignmentRepository.findById(assignmentId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Không tìm thấy phân công: "
                                                + assignmentId
                                )
                        );

        Long deviceId = existing.getDevice().getId();
        Long driverId = existing.getDriver().getId();

        // Khóa theo thứ tự cố định.
        deviceRepository.findByIdForUpdate(deviceId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Thiết bị không còn tồn tại"
                        )
                );

        driverRepository.findByIdForUpdate(driverId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Tài xế không còn tồn tại"
                        )
                );

        // Khóa bản ghi phân công trước khi cập nhật.
        DeviceAssignmentEntity assignment =
                assignmentRepository.findByIdForUpdate(assignmentId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Không tìm thấy phân công: "
                                                + assignmentId
                                )
                        );

        if (assignment.getEndedAt() != null) {
            throw new IllegalStateException(
                    "Phân công đã kết thúc"
            );
        }

        validateTimeRange(
                assignment.getStartedAt(),
                endedAt
        );

        // endedAt đã được Controller chuyển sang UTC.
        // Vì vậy thời gian hiện tại cũng phải sử dụng UTC.
        if (endedAt.isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new IllegalArgumentException(
                    "Thời điểm kết thúc thực tế không được ở tương lai"
            );
        }

        assignment.setEndedAt(endedAt);

        DeviceAssignmentEntity savedAssignment =
                assignmentRepository.saveAndFlush(assignment);

        entityManager.refresh(savedAssignment);

        return savedAssignment;
    }


    //Tìm phân công đang hoạt động của thiết bị.
    @Override
    @Transactional(readOnly = true)
    public Optional<DeviceAssignmentEntity>
    getActiveAssignmentByDevice(Long deviceId) {
        requireId(deviceId, "deviceId");

        return assignmentRepository
                .findByDevice_IdAndEndedAtIsNull(deviceId);
    }

    //Tìm phân công đang hoạt động của tài xế.
    @Override
    @Transactional(readOnly = true)
    public Optional<DeviceAssignmentEntity>
    getActiveAssignmentByDriver(Long driverId) {
        requireId(driverId, "driverId");

        return assignmentRepository
                .findByDriver_IdAndEndedAtIsNull(driverId);
    }

    // Lấy lịch sử phân công của thiết bị.
    @Override
    @Transactional(readOnly = true)
    public List<DeviceAssignmentEntity>
    getDeviceAssignmentHistory(Long deviceId) {
        requireId(deviceId, "deviceId");

        return assignmentRepository
                .findByDevice_IdOrderByStartedAtDescIdDesc(deviceId);
    }

    //Lấy lịch sử phân công của tài xế.
    @Override
    @Transactional(readOnly = true)
    public List<DeviceAssignmentEntity>
    getDriverAssignmentHistory(Long driverId) {
        requireId(driverId, "driverId");

        return assignmentRepository
                .findByDriver_IdOrderByStartedAtDescIdDesc(driverId);
    }

    //Xác định phân công tại thời điểm đo.
    @Override
    @Transactional(readOnly = true)
    public Optional<DeviceAssignmentEntity>
    findAssignmentAtTime(
            Long deviceId,
            LocalDateTime measuredAt
    ) {
        requireId(deviceId, "deviceId");

        if (measuredAt == null) {
            throw new IllegalArgumentException(
                    "measuredAt không được để trống"
            );
        }

        List<DeviceAssignmentEntity> matches =
                assignmentRepository.findAssignmentsAtTime(
                        deviceId,
                        measuredAt
                );

        if (matches.isEmpty()) {
            return Optional.empty();
        }

        if (matches.size() > 1) {
            throw new IllegalStateException(
                    "Phát hiện nhiều phân công chồng lấn "
                            + "cho thiết bị " + deviceId
            );
        }

        return Optional.of(matches.get(0));
    }

    /**
     * Xác định phân công có hiệu lực của thiết bị
     * tại thời điểm được chỉ định.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<DeviceAssignmentEntity>
    getActiveAssignmentByDeviceAtTime(
            Long deviceId,
            LocalDateTime atTime
    ) {
        requireId(deviceId, "deviceId");

        if (atTime == null) {
            throw new IllegalArgumentException(
                    "atTime không được để trống"
            );
        }

        List<DeviceAssignmentEntity> assignments =
                assignmentRepository.findActiveByDeviceAtTime(
                        deviceId,
                        atTime
                );

        if (assignments.size() > 1) {
            throw new IllegalStateException(
                    "Phát hiện nhiều phân công có hiệu lực "
                            + "cho thiết bị " + deviceId
            );
        }

        return assignments.stream().findFirst();
    }

    /**
     * Xác định phân công có hiệu lực của tài xế
     * tại thời điểm được chỉ định.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<DeviceAssignmentEntity>
    getActiveAssignmentByDriverAtTime(
            Long driverId,
            LocalDateTime atTime
    ) {
        requireId(driverId, "driverId");

        if (atTime == null) {
            throw new IllegalArgumentException(
                    "atTime không được để trống"
            );
        }

        List<DeviceAssignmentEntity> assignments =
                assignmentRepository.findActiveByDriverAtTime(
                        driverId,
                        atTime
                );

        if (assignments.size() > 1) {
            throw new IllegalStateException(
                    "Phát hiện nhiều phân công có hiệu lực "
                            + "cho tài xế " + driverId
            );
        }

        return assignments.stream().findFirst();
    }

    private void validateDevice(DeviceEntity device){
        if (device.getStatus() == null
                || device.getStatus()
                    == DeviceEntity.DeviceStatus.MAINTENANCE
                || device.getStatus()
                    == DeviceEntity.DeviceStatus.INACTIVE) {

            throw new IllegalStateException(
                    "Thiết bị không đủ điều kiện phân công"
            );
        }
    }

    private void validateDriver(DriverEntity driver) {
        if (driver.getUser() == null
                || !Boolean.TRUE.equals(
                        driver.getUser().getActive()
                )
                || driver.getUser().getRole() == null
                || !"DRIVER".equals(driver.getUser().getRole().getName()
        )) {
            throw new IllegalStateException(
                    "Tài xế không hợp lệ hoặc tài khoản không hoạt động"
            );
        }
    }

    private void validateTimeRange(
            LocalDateTime startedAt,
            LocalDateTime endedAt
    ){
        if (startedAt == null){
            throw new IllegalArgumentException(
                    "Thời điểm bắt đầu không được để trống"
            );
        }

        if (endedAt != null
                && !endedAt.isAfter(startedAt)){
            throw new IllegalArgumentException(
                    "Thời điểm kết thúc phải sau thời điểm bắt đầu"
            );
        }
    }

    private void requireId(Long id, String fieldName){
        if (id == null || id <= 0){
            throw new IllegalArgumentException(
                    fieldName + " phải là số nguyên dương"
            );
        }
    }
}

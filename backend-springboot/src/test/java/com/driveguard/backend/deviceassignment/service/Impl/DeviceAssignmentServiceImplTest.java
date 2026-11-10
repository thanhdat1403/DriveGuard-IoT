
package com.driveguard.backend.deviceassignment.service.Impl;

import com.driveguard.backend.device.entity.DeviceEntity;
import com.driveguard.backend.device.repository.DeviceRepository;
import com.driveguard.backend.deviceassignment.repository.DeviceAssignmentRepository;
import com.driveguard.backend.driver.entity.DriverEntity;
import com.driveguard.backend.driver.repository.DriverRepository;
import com.driveguard.backend.role.entity.RoleEntity;
import com.driveguard.backend.user.entity.UserEntity;
import com.driveguard.backend.deviceassignment.entity.DeviceAssignmentEntity;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceAssignmentServiceImplTest {

    @Mock
    private DeviceAssignmentRepository assignmentRepository;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private DriverRepository driverRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private DeviceAssignmentServiceImpl assignmentService;


    @Test
    void assignDevice_shouldRejectWhenDeviceOverlaps() {

        // 1. Dữ liệu kiểm thử
        Long deviceId = 1L;
        Long driverId = 1L;

        LocalDateTime startedAt =
                LocalDateTime.of(2026, 11, 10, 8, 0);

        LocalDateTime endedAt =
                LocalDateTime.of(2026, 11, 10, 10, 0);

        // 2. Thiết bị hợp lệ
        DeviceEntity device = mock(DeviceEntity.class);

        when(device.getStatus())
                .thenReturn(DeviceEntity.DeviceStatus.ONLINE);

        // 3. Role DRIVER
        RoleEntity role = new RoleEntity();
        role.setId(4L);
        role.setName("DRIVER");

        // 4. Tài khoản đang hoạt động
        UserEntity user = new UserEntity();
        user.setId(10L);
        user.setUsername("driver_test");
        user.setActive(true);
        user.setRole(role);

        // 5. Hồ sơ tài xế
        DriverEntity driver = new DriverEntity();
        driver.setId(driverId);
        driver.setUser(user);

        // 6. Giả lập Repository
        when(deviceRepository.findByIdForUpdate(deviceId))
                .thenReturn(Optional.of(device));

        when(driverRepository.findByIdForUpdate(driverId))
                .thenReturn(Optional.of(driver));

        // Thiết bị đã có phân công chồng lấn.
        when(
                assignmentRepository
                        .countOverlappingDeviceAssignments(
                                deviceId,
                                startedAt,
                                endedAt
                        )
        ).thenReturn(1L);

        // 7. Thực hiện nghiệp vụ và kiểm tra lỗi
        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> assignmentService.assignDevice(
                                deviceId,
                                driverId,
                                startedAt,
                                endedAt
                        )
                );

        // 8. Kiểm tra nội dung thông báo
        assertEquals(
                "Thiết bị đã được phân công trong khoảng thời gian này",
                exception.getMessage()
        );

        // 9. Không được lưu phân công mới
        verify(assignmentRepository, never())
                .save(any());

        // 10. Không cần kiểm tra trùng tài xế
        // vì thiết bị đã bị trùng lịch.
        verify(
                assignmentRepository,
                never()
        ).countOverlappingDriverAssignments(
                anyLong(),
                any(),
                any()
        );
    }


    @Test
    void assignDevice_shouldSaveWhenAssignmentIsValid() {

        // 1. Chuẩn bị dữ liệu
        Long deviceId = 1L;
        Long driverId = 1L;

        LocalDateTime startedAt =
                LocalDateTime.of(2026, 11, 10, 8, 0);

        LocalDateTime endedAt =
                LocalDateTime.of(2026, 11, 10, 10, 0);

        // 2. Giả lập thiết bị ONLINE
        DeviceEntity device = mock(DeviceEntity.class);

        when(device.getStatus())
                .thenReturn(DeviceEntity.DeviceStatus.ONLINE);

        // 3. Tạo Role DRIVER
        RoleEntity role = new RoleEntity();
        role.setName("DRIVER");

        // 4. Tạo tài khoản hợp lệ
        UserEntity user = new UserEntity();
        user.setUsername("driver_test");
        user.setActive(true);
        user.setRole(role);

        // 5. Tạo hồ sơ tài xế
        DriverEntity driver = new DriverEntity();
        driver.setUser(user);

        // 6. Giả lập tìm thiết bị và tài xế
        when(deviceRepository.findByIdForUpdate(deviceId))
                .thenReturn(Optional.of(device));

        when(driverRepository.findByIdForUpdate(driverId))
                .thenReturn(Optional.of(driver));

        // 7. Không có phân công chồng lấn
        when(assignmentRepository
                .countOverlappingDeviceAssignments(
                        deviceId, startedAt, endedAt))
                .thenReturn(0L);

        when(assignmentRepository
                .countOverlappingDriverAssignments(
                        driverId, startedAt, endedAt))
                .thenReturn(0L);

        // 8. Giả lập Repository trả về Entity đã lưu
        when(assignmentRepository.saveAndFlush(
                any(DeviceAssignmentEntity.class)
        )).thenAnswer(invocation -> invocation.getArgument(0));

        // 9. Gọi Service
        DeviceAssignmentEntity result =
                assignmentService.assignDevice(
                        deviceId,
                        driverId,
                        startedAt,
                        endedAt
                );

        // 10. Kiểm tra kết quả
        assertNotNull(result);

        assertSame(device, result.getDevice());
        assertSame(driver, result.getDriver());

        assertEquals(startedAt, result.getStartedAt());
        assertEquals(endedAt, result.getEndedAt());

        // 11. Kiểm tra đối tượng được lưu

        // Khởi tạo ArgumentCaptor để bắt Entity truyền vào Repository
        ArgumentCaptor<DeviceAssignmentEntity> captor =
                ArgumentCaptor.forClass(DeviceAssignmentEntity.class);

        // Xác nhận Service gọi saveAndFlush đúng một lần
        verify(assignmentRepository, times(1))
                .saveAndFlush(captor.capture());

        // Xác nhận Service đồng bộ lại Entity từ database
        verify(entityManager, times(1))
                .refresh(any(DeviceAssignmentEntity.class));

        // Lấy Entity đã được truyền vào saveAndFlush
        DeviceAssignmentEntity saved = captor.getValue();

        // Kiểm tra dữ liệu phân công
        assertSame(device, saved.getDevice());
        assertSame(driver, saved.getDriver());
        assertEquals(startedAt, saved.getStartedAt());
        assertEquals(endedAt, saved.getEndedAt());

        // 12. Xác nhận hai kiểm tra trùng lịch
        // đều được thực hiện đúng một lần
        verify(assignmentRepository, times(1))
                .countOverlappingDeviceAssignments(
                        deviceId, startedAt, endedAt
                );

        verify(assignmentRepository, times(1))
                .countOverlappingDriverAssignments(
                        driverId, startedAt, endedAt
                );
    }


    @Test
    void assignDevice_shouldRejectWhenDriverOverlaps() {

        // 1. Dữ liệu phân công mới
        Long deviceId = 2L;
        Long driverId = 1L;

        LocalDateTime startedAt =
                LocalDateTime.of(2026, 11, 10, 9, 0);

        LocalDateTime endedAt =
                LocalDateTime.of(2026, 11, 10, 11, 0);

        // 2. Giả lập thiết bị hợp lệ
        DeviceEntity device = mock(DeviceEntity.class);

        when(device.getStatus())
                .thenReturn(DeviceEntity.DeviceStatus.ONLINE);

        // 3. Role DRIVER
        RoleEntity role = new RoleEntity();
        role.setName("DRIVER");

        // 4. Tài khoản đang hoạt động
        UserEntity user = new UserEntity();
        user.setUsername("driver_test");
        user.setActive(true);
        user.setRole(role);

        // 5. Hồ sơ tài xế
        DriverEntity driver = new DriverEntity();
        driver.setUser(user);

        // 6. Giả lập Repository
        when(deviceRepository.findByIdForUpdate(deviceId))
                .thenReturn(Optional.of(device));

        when(driverRepository.findByIdForUpdate(driverId))
                .thenReturn(Optional.of(driver));

        // Thiết bị mới chưa bị trùng lịch
        when(assignmentRepository
                .countOverlappingDeviceAssignments(
                        deviceId,
                        startedAt,
                        endedAt
                ))
                .thenReturn(0L);

        // Tài xế đã có phân công chồng lấn
        when(assignmentRepository
                .countOverlappingDriverAssignments(
                        driverId,
                        startedAt,
                        endedAt
                ))
                .thenReturn(1L);

        // 7. Thực hiện và kiểm tra ngoại lệ
        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> assignmentService.assignDevice(
                                deviceId,
                                driverId,
                                startedAt,
                                endedAt
                        )
                );

        // 8. Kiểm tra thông báo lỗi
        assertEquals(
                "Tài xế đã có phân công trong khoảng thời gian này",
                exception.getMessage()
        );

        // 9. Xác nhận đã kiểm tra trùng lịch
        verify(assignmentRepository, times(1))
                .countOverlappingDeviceAssignments(
                        deviceId,
                        startedAt,
                        endedAt
                );

        verify(assignmentRepository, times(1))
                .countOverlappingDriverAssignments(
                        driverId,
                        startedAt,
                        endedAt
                );

        // 10. Không được lưu phân công mới
        verify(assignmentRepository, never())
                .save(any(DeviceAssignmentEntity.class));
    }


    @Test
    void assignDevice_shouldRejectWhenDeviceIsMaintenance() {

        Long deviceId = 1L;
        Long driverId = 1L;

        LocalDateTime startedAt =
                LocalDateTime.of(2026, 11, 10, 8, 0);

        LocalDateTime endedAt =
                LocalDateTime.of(2026, 11, 10, 10, 0);

        // Thiết bị đang bảo trì
        DeviceEntity device = mock(DeviceEntity.class);

        when(device.getStatus())
                .thenReturn(
                        DeviceEntity.DeviceStatus.MAINTENANCE
                );

        // Tài xế hợp lệ
        RoleEntity role = new RoleEntity();
        role.setName("DRIVER");

        UserEntity user = new UserEntity();
        user.setActive(true);
        user.setRole(role);

        DriverEntity driver = new DriverEntity();
        driver.setUser(user);

        when(deviceRepository.findByIdForUpdate(deviceId))
                .thenReturn(Optional.of(device));

        when(driverRepository.findByIdForUpdate(driverId))
                .thenReturn(Optional.of(driver));

        // Gọi Service
        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> assignmentService.assignDevice(
                                deviceId,
                                driverId,
                                startedAt,
                                endedAt
                        )
                );

        assertEquals(
                "Thiết bị không đủ điều kiện phân công",
                exception.getMessage()
        );

        // Không được lưu dữ liệu
        verify(assignmentRepository, never())
                .save(any(DeviceAssignmentEntity.class));

        // Không cần kiểm tra lịch khi thiết bị không hợp lệ
        verify(assignmentRepository, never())
                .countOverlappingDeviceAssignments(
                        anyLong(), any(), any()
                );

        verify(assignmentRepository, never())
                .countOverlappingDriverAssignments(
                        anyLong(), any(), any()
                );
    }


    @Test
    void assignDevice_shouldRejectWhenDriverAccountIsInactive() {

        Long deviceId = 1L;
        Long driverId = 1L;

        LocalDateTime startedAt =
                LocalDateTime.of(2026, 11, 10, 8, 0);

        LocalDateTime endedAt =
                LocalDateTime.of(2026, 11, 10, 10, 0);

        // Thiết bị ONLINE
        DeviceEntity device = mock(DeviceEntity.class);

        when(device.getStatus())
                .thenReturn(DeviceEntity.DeviceStatus.ONLINE);

        // Role DRIVER
        RoleEntity role = new RoleEntity();
        role.setName("DRIVER");

        // Tài khoản đã bị vô hiệu hóa
        UserEntity user = new UserEntity();
        user.setUsername("driver_inactive");
        user.setActive(false);
        user.setRole(role);

        DriverEntity driver = new DriverEntity();
        driver.setUser(user);

        when(deviceRepository.findByIdForUpdate(deviceId))
                .thenReturn(Optional.of(device));

        when(driverRepository.findByIdForUpdate(driverId))
                .thenReturn(Optional.of(driver));

        // Gọi Service
        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> assignmentService.assignDevice(
                                deviceId,
                                driverId,
                                startedAt,
                                endedAt
                        )
                );

        assertEquals(
                "Tài xế không hợp lệ hoặc tài khoản không hoạt động",
                exception.getMessage()
        );

        // Không được lưu phân công
        verify(assignmentRepository, never())
                .save(any(DeviceAssignmentEntity.class));

        // Không kiểm tra lịch khi tài xế không hợp lệ
        verify(assignmentRepository, never())
                .countOverlappingDeviceAssignments(
                        anyLong(), any(), any()
                );

        verify(assignmentRepository, never())
                .countOverlappingDriverAssignments(
                        anyLong(), any(), any()
                );
    }



    @Test
    void endAssignment_shouldEndSuccessfully() {

        Long assignmentId = 1L;
        Long deviceId = 1L;
        Long driverId = 1L;


        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);

        LocalDateTime startedAt = nowUtc.minusHours(3);

        LocalDateTime endedAt = nowUtc.minusHours(1);


        DeviceEntity device = mock(DeviceEntity.class);
        DriverEntity driver = mock(DriverEntity.class);

        when(device.getId()).thenReturn(deviceId);
        when(driver.getId()).thenReturn(driverId);

        DeviceAssignmentEntity assignment =
                new DeviceAssignmentEntity();

        assignment.setId(assignmentId);
        assignment.setDevice(device);
        assignment.setDriver(driver);
        assignment.setStartedAt(startedAt);
        assignment.setEndedAt(null);

        when(assignmentRepository.findById(assignmentId))
                .thenReturn(Optional.of(assignment));

        when(deviceRepository.findByIdForUpdate(deviceId))
                .thenReturn(Optional.of(device));

        when(driverRepository.findByIdForUpdate(driverId))
                .thenReturn(Optional.of(driver));

        when(assignmentRepository.findByIdForUpdate(assignmentId))
                .thenReturn(Optional.of(assignment));

        when(assignmentRepository.saveAndFlush(
                any(DeviceAssignmentEntity.class)
        )).thenAnswer(invocation -> invocation.getArgument(0));

        DeviceAssignmentEntity result =
                assignmentService.endAssignment(
                        assignmentId,
                        endedAt
                );

        assertNotNull(result);
        assertEquals(endedAt, result.getEndedAt());

        verify(assignmentRepository, times(1))
                .saveAndFlush(assignment);

        verify(entityManager, times(1))
                .refresh(assignment);
    }


    @Test
    void endAssignment_shouldRejectWhenAlreadyEnded() {

        Long assignmentId = 1L;
        Long deviceId = 1L;
        Long driverId = 1L;

        LocalDateTime startedAt =
                LocalDateTime.now().minusHours(4);

        LocalDateTime previousEndedAt =
                LocalDateTime.now().minusHours(2);

        LocalDateTime newEndedAt =
                LocalDateTime.now().minusHours(1);

        DeviceEntity device = mock(DeviceEntity.class);
        DriverEntity driver = mock(DriverEntity.class);

        when(device.getId()).thenReturn(deviceId);
        when(driver.getId()).thenReturn(driverId);

        DeviceAssignmentEntity assignment =
                new DeviceAssignmentEntity();

        assignment.setId(assignmentId);
        assignment.setDevice(device);
        assignment.setDriver(driver);
        assignment.setStartedAt(startedAt);
        assignment.setEndedAt(previousEndedAt);

        when(assignmentRepository.findById(assignmentId))
                .thenReturn(Optional.of(assignment));

        when(deviceRepository.findByIdForUpdate(deviceId))
                .thenReturn(Optional.of(device));

        when(driverRepository.findByIdForUpdate(driverId))
                .thenReturn(Optional.of(driver));

        when(assignmentRepository.findByIdForUpdate(assignmentId))
                .thenReturn(Optional.of(assignment));

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> assignmentService.endAssignment(
                                assignmentId,
                                newEndedAt
                        )
                );

        assertEquals(
                "Phân công đã kết thúc",
                exception.getMessage()
        );

        assertEquals(
                previousEndedAt,
                assignment.getEndedAt()
        );

        verify(assignmentRepository, never())
                .save(any(DeviceAssignmentEntity.class));
    }


    @Test
    void findAssignmentAtTime_shouldReturnCorrectAssignment() {

        Long deviceId = 1L;

        LocalDateTime startedAt =
                LocalDateTime.of(2026, 11, 10, 8, 0);

        LocalDateTime endedAt =
                LocalDateTime.of(2026, 11, 10, 10, 0);

        LocalDateTime measuredAt =
                LocalDateTime.of(2026, 11, 10, 9, 30);

        DeviceAssignmentEntity assignment =
                new DeviceAssignmentEntity();

        assignment.setId(1L);
        assignment.setStartedAt(startedAt);
        assignment.setEndedAt(endedAt);

        when(assignmentRepository.findAssignmentsAtTime(
                deviceId, measuredAt
        )).thenReturn(List.of(assignment));

        Optional<DeviceAssignmentEntity> result =
                assignmentService.findAssignmentAtTime(
                        deviceId, measuredAt
                );

        assertTrue(result.isPresent());
        assertSame(assignment, result.get());

        verify(assignmentRepository, times(1))
                .findAssignmentsAtTime(
                        deviceId, measuredAt
                );
    }


    @Test
    void findAssignmentAtTime_shouldRejectMultipleAssignments() {

        Long deviceId = 1L;

        LocalDateTime measuredAt =
                LocalDateTime.of(2026, 11, 10, 9, 30);

        DeviceAssignmentEntity assignment1 =
                new DeviceAssignmentEntity();

        assignment1.setId(1L);

        DeviceAssignmentEntity assignment2 =
                new DeviceAssignmentEntity();

        assignment2.setId(2L);

        when(assignmentRepository.findAssignmentsAtTime(
                deviceId, measuredAt
        )).thenReturn(
                List.of(assignment1, assignment2)
        );

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> assignmentService.findAssignmentAtTime(
                                deviceId, measuredAt
                        )
                );

        assertEquals(
                "Phát hiện nhiều phân công chồng lấn "
                        + "cho thiết bị " + deviceId,
                exception.getMessage()
        );
    }


    @Test
    void getActiveAssignmentByDeviceAtTime_shouldReturnAssignment() {

        Long deviceId = 1L;
        LocalDateTime atTime =
                LocalDateTime.of(2026, 11, 10, 9, 0);

        DeviceAssignmentEntity assignment =
                new DeviceAssignmentEntity();

        assignment.setId(10L);

        when(assignmentRepository.findActiveByDeviceAtTime(
                deviceId, atTime
        )).thenReturn(List.of(assignment));

        Optional<DeviceAssignmentEntity> result =
                assignmentService.getActiveAssignmentByDeviceAtTime(
                        deviceId, atTime
                );

        assertTrue(result.isPresent());
        assertSame(assignment, result.get());

        verify(assignmentRepository).findActiveByDeviceAtTime(
                deviceId, atTime
        );
    }

    @Test
    void getActiveAssignmentByDeviceAtTime_shouldReturnEmpty() {

        Long deviceId = 1L;
        LocalDateTime atTime =
                LocalDateTime.of(2026, 11, 10, 9, 0);

        when(assignmentRepository.findActiveByDeviceAtTime(
                deviceId, atTime
        )).thenReturn(List.of());

        Optional<DeviceAssignmentEntity> result =
                assignmentService.getActiveAssignmentByDeviceAtTime(
                        deviceId, atTime
                );

        assertTrue(result.isEmpty());
    }

    @Test
    void getActiveAssignmentByDeviceAtTime_shouldRejectOverlaps() {

        Long deviceId = 1L;
        LocalDateTime atTime =
                LocalDateTime.of(2026, 11, 10, 9, 0);

        DeviceAssignmentEntity first =
                new DeviceAssignmentEntity();
        first.setId(10L);

        DeviceAssignmentEntity second =
                new DeviceAssignmentEntity();
        second.setId(11L);

        when(assignmentRepository.findActiveByDeviceAtTime(
                deviceId, atTime
        )).thenReturn(List.of(first, second));

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> assignmentService
                                .getActiveAssignmentByDeviceAtTime(
                                        deviceId, atTime
                                )
                );

        assertEquals(
                "Phát hiện nhiều phân công có hiệu lực "
                        + "cho thiết bị " + deviceId,
                exception.getMessage()
        );
    }

    @Test
    void getActiveAssignmentByDriverAtTime_shouldReturnAssignment() {

        Long driverId = 2L;
        LocalDateTime atTime =
                LocalDateTime.of(2026, 11, 10, 9, 0);

        DeviceAssignmentEntity assignment =
                new DeviceAssignmentEntity();
        assignment.setId(20L);

        when(assignmentRepository.findActiveByDriverAtTime(
                driverId, atTime
        )).thenReturn(List.of(assignment));

        Optional<DeviceAssignmentEntity> result =
                assignmentService.getActiveAssignmentByDriverAtTime(
                        driverId, atTime
                );

        assertTrue(result.isPresent());
        assertSame(assignment, result.get());

        verify(assignmentRepository).findActiveByDriverAtTime(
                driverId, atTime
        );
    }

}

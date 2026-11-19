package com.driveguard.backend.device.service.Impl;

import com.driveguard.backend.device.entity.DeviceEntity;
import com.driveguard.backend.device.repository.DeviceRepository;
import com.driveguard.backend.device.service.DeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * @Slf4j: Tự động tạo biến 'log' của thư viện Lombok để ghi nhật ký hệ thống chuyên nghiệp.
 *
 * @Service: Đánh dấu class Triển khai (Implementation) này là một Spring Bean tầng Service.
 * Khi các tầng khác (như Controller hoặc MQTT Service) gọi Interface `DeviceService`,
 * Spring sẽ tự động tìm đến và chạy logic trong Class `DeviceServiceImpl` này.
 *
 * @RequiredArgsConstructor: Tự động tạo Constructor cho biến `private final DeviceRepository` ngầm định.
 *
 * implements DeviceService: Ép buộc Class này phải triển khai (viết code cụ thể) cho tất cả các hàm
 * đã được định nghĩa bên trong Interface `DeviceService`.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceServiceImpl implements DeviceService {

    // Khai báo kết nối cơ sở dữ liệu cho bảng Thiết bị (Được tiêm tự động qua Constructor của Lombok)
    private final DeviceRepository deviceRepository;

    /**
     * @Override: Đánh dấu đây là hàm ghi đè/triển khai lại từ Interface `DeviceService`.
     *
     * @Transactional: Đảm bảo tính toàn vẹn dữ liệu. Nếu có bất kỳ lỗi nào xảy ra trong quá trình
     * kiểm tra hoặc lưu, toàn bộ hành động thay đổi dữ liệu trong hàm này sẽ bị hủy bỏ (Rollback).
     */
    @Override
    @Transactional
    public void markDeviceOnline(String deviceCode) {

        // Bước 1: kiểm tra đầu vào (validation)
        // Nếu mã thiết bị truyền vào bị rỗng, null hoặc toàn khoảng trắng thì dừng lại và báo lỗi lập tức.
        if (deviceCode == null || deviceCode.isBlank()){
            throw new IllegalArgumentException(
                    "Ma thiet bi khong hop le"
            );
        }

        // BƯỚC 2: TÌM KIẾM THIẾT BỊ TRONG DATABASE
        // Thực hiện câu lệnh SELECT tìm thiết bị theo 'deviceCode'.
        // Nếu không tìm thấy bất kỳ bản ghi nào trùng khớp -> ném ra lỗi thông báo.
        DeviceEntity device = deviceRepository
                .findByDeviceCodeForUpdate(deviceCode.trim())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Khong tim thay thiet bi: " + deviceCode
                ));

        // BƯỚC 3: KIỂM TRA QUY TẮC NGHIỆP VỤ (BUSINESS RULE)
        // Nếu thiết bị đang ở trạng thái ngừng hoạt động (INACTIVE) hoặc đang mang đi sửa (MAINTENANCE)
        // thì hệ thống SẼ TỪ CHỐI không cho phép thiết bị kết nối vào và ném ra lỗi trạng thái.
        if (device.getStatus() == DeviceEntity.DeviceStatus.INACTIVE
                || device.getStatus() == DeviceEntity.DeviceStatus.MAINTENANCE){
            throw new IllegalStateException(
                    "Thiet bi khong duoc phep hoat dong: " + deviceCode
            );
        }

        // BƯỚC 4: THAY ĐỔI TRẠNG THÁI VÀ THỜI GIAN
        // Cập nhật trạng thái của thực thể thiết bị thành ONLINE
        device.setStatus(DeviceEntity.DeviceStatus.ONLINE);

        // Ghi nhận mốc thời gian hệ thống nhìn thấy thiết bị tương tác là NGAY BÂY GIỜ
        device.setLastSeenAt(LocalDateTime.now(ZoneOffset.UTC));

        // BƯỚC 5: LƯU LẠI VÀO DATABASE
        // Thực hiện câu lệnh UPDATE để cập nhật hai trường (Status và LastSeenAt) xuống cơ sở dữ liệu
        deviceRepository.save(device);

        // Ghi lại nhật ký thành công vào hệ thống Log kèm theo mã thiết bị và ID của thiết bị đó
        log.info(
                "[DEVICE] Da cap nhat ONLINE: {} | ID: {}",
                device.getDeviceCode(),
                device.getId()
        );

    }

    /**
     * Chuyen cac thiet bi ONLINE da qua han Heartbeat
     * sang OFFLINE.
     *
     * cutoffTime phai duoc tinh theo UTC.
     *
     * @return So thiet bi thuc su duoc chuyen OFFLINE.
     */
    @Override
    @Transactional
    public int markExpiredDevicesOffline(LocalDateTime cutoffTime) {

        // Bước 1: Kiểm tra đầu vào
        if (cutoffTime == null){
            throw new IllegalArgumentException(
                    "cutoffTime khong duoc null"
            );
        }

        // Bước 2: Tìm các thiết bị có Heartbeat quá hạn
        List<DeviceEntity> expiredDevices =
                deviceRepository.findDevicesWithExpiredHeartbeat(
                        DeviceEntity.DeviceStatus.ONLINE,
                        cutoffTime
                );

        int offlineCount = 0;

        // Bước 3: Xử lý từng thiết bị
        for (DeviceEntity candidate : expiredDevices) {

            // Khóa bản ghi để tránh xung đột với MQTT
            DeviceEntity device = deviceRepository
                    .findByIdForUpdate(candidate.getId())
                    .orElse(null);

            if (device == null){
                continue;
            }

            // Bước 4: Kiểm tra lại trạng thái sau khi khóa
            if (device.getStatus() != DeviceEntity.DeviceStatus.ONLINE){
                continue;
            }

            LocalDateTime lastSeenAt = device.getLastSeenAt();

            // Chỉ chuyển OFFLINE nếu chưa từng có Heartbeat
            // hoặc Heartbeat cu hon cutoffTime
            boolean expired = lastSeenAt == null
                    || lastSeenAt.isBefore(cutoffTime);

            if (!expired){
                continue;
            }

            // Bước 5: Chuyển sang OFFLINE
            device.setStatus(DeviceEntity.DeviceStatus.OFFLINE);

            deviceRepository.save(device);

            offlineCount++;

            log.warn(
                    "[HEARTBEAT] Device OFFLINE | deviceCode={} | lastSeenAt={} | cutoffTime={}",
                    device.getDeviceCode(),
                    lastSeenAt,
                    cutoffTime
            );
        }

        // Bước 6: Ghi log tổng kết
        if (offlineCount > 0){
            log.info(
                    "[HEARTBEAT] Tong so thiet bi chuyen OFFLINE: {}",
                    offlineCount
            );
        }
        return offlineCount;
    }
}

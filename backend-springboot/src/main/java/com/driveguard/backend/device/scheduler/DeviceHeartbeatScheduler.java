package com.driveguard.backend.device.scheduler;

import com.driveguard.backend.device.service.DeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Slf4j // Tự động tạo biến log để ghi nhật ký hệ thống
@Component // Khai báo đây là một thành phần được Spring Boot quản lý
@Profile("e2e") // Chỉ chạy bộ lập lịch này trong môi trường test "e2e"
@RequiredArgsConstructor // Tự động tạo Constructor cho biến deviceService ở dưới
@ConditionalOnProperty( // Điều kiện: Chỉ kích hoạt khi thuộc tính này bằng "true" trong file cấu hình
        name = "driveguard.heartbeat.enabled",
        havingValue = "true"
)
public class DeviceHeartbeatScheduler {

    private final DeviceService deviceService;

    // Lấy thời gian hết hạn từ file cấu hình (tính bằng mili-giây), mặc định là 30000ms (30 giây) nếu không khai báo
    @org.springframework.beans.factory.annotation.Value(
            "${driveguard.heartbeat.offline-timeout-ms:30000}"
    )
    private long offlineTimeoutMs;

    // Tự động chạy lại hàm này sau một khoảng thời gian cấu hình, mặc định là mỗi 5000ms (5 giây)
    @Scheduled(
            fixedDelayString = "${driveguard.heartbeat.check-interval-ms:5000}"
    )
    public void checkExpiredHeartbeats() {

        try {
            // Kiểm tra tính hợp lệ của cấu hình thời gian hết hạn
            if (offlineTimeoutMs <= 0){
                log.error(
                        "[HEARTBEAT] Cấu hình thời gian ngoại tuyến không hợp lệ: {}",
                        offlineTimeoutMs
                );
                return; // Dừng lại không chạy tiếp nếu cấu hình sai
            }

            //Tính toán mốc thời gian giới hạn (cutoff Time) theo giờ chuẩn UTC
            //Ví dụ: Hiện tại là 10:00:00, trừ đi 30 giây -> Mốc thời gian là 09:59:30
            LocalDateTime cutoFFTime = LocalDateTime
                    .now(ZoneOffset.UTC)
                    .minusNanos(offlineTimeoutMs * 1_000_000L); // Đổi từ mili-giây sang nano-giây

            // Gọi Service cập nhật Databas: Thiết bị nào có thời gian cập nhật cuối cùng
            // TRƯỚC mốc cutoffTime thì sẽ bị đổi trạng thái sang OFFLINE. Đống thời trả về số lượng bị đổi.
            int offlineCount =
                    deviceService.markExpiredDevicesOffline(cutoFFTime);

            // Nếu có thiết bị nào bị ngắt kết nối thì ghi log cảnh báo ra hệ thống
            if (offlineCount > 0){
                log.warn(
                        "[HEARTBEAT] Đã chuyển {} thiết bị sang trạng thái ngoại tuyến (OFFLINE)",
                        offlineCount
                );
            }
        } catch (Exception ex) {
            // Bắt các lỗi ngoại lệ (nếu có) để tránh làm sập bộ lập lịch ở các chu kỳ sau
            log.error(
                    "[HEARTBEAT] Bộ lập lịch gặp lỗi hệ thống",
                    ex
            );
        }
    }
}

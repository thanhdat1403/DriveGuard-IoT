package com.driveguard.backend.dashboard.dto.response;

import java.time.LocalDateTime;

/**
 * Record đóng vai trò là DTO chứa dữ liệu tổng quan hiển thị trên Dashboard.
 * Tất cả các trường dữ liệu ở đây đều là 'final' (không thể sửa đổi sau khi tạo).
 */
public record DashboardSummaryResponse(

        Long deviceId,          // ID của thiết bị cần xem thống kê

        // --- Thống kê dữ liệu cảm biến ---
        long totalReadings,     // Tổng số bản tin cảm biến nhận được

        // --- Thống kê cảnh báo ---
        long totalAlerts,       // Tổng số cảnh báo đã từng xảy ra
        long activeSosAlerts,   // Số cảnh báo SOS hiện đang hoạt động (chưa đóng)
        long unresolvedAlerts,  // Số cảnh báo thông thường chưa được xử lý xong

        // --- Dữ liệu cảm biến mới nhất ---
        LatestSensor latestSensor // Đối tượng chứa thông tin cảm biến mới nhất vừa nhận được

) {

    /**
     * Record con nằm bên trong, đại diện cho bản tin cảm biến gần đây nhất của thiết bị.
     * Cách lấy dữ liệu: ví dụ 'response.latestSensor().heartRate()'
     */
    public record LatestSensor(

            Long readingId,          // ID của bản ghi dữ liệu cảm biến
            Double heartRate,        // Nhịp tim của tài xế (bpm)
            Double spo2,             // Nồng độ oxy trong máu (%)
            String edgeAiStatus,     // Trạng thái phân tích từ AI tại thiết bị (Edge AI)
            Boolean isDrowsy,        // Tài xế có đang ngủ gật hay không (true/false)
            LocalDateTime measuredAt // Thời điểm thiết bị đo được các chỉ số này

    ) {
    }
}

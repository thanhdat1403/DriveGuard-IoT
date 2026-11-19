package com.driveguard.backend.location.mqtt;

import org.springframework.stereotype.Component; // Đánh dấu class là Bean để Spring quản lý
import tools.jackson.databind.JsonNode; // Đại diện cho cấu trúc cây JSON của thư viện Jackson

import java.math.BigDecimal; // Xử lý số thập phân chính xác, kiểm tra độ dài chữ số
import java.time.Instant; // Đại diện cho một điểm thời gian trên trục UTC
import java.time.LocalDateTime; // Lưu trữ ngày giờ không kèm múi giờ
import java.time.ZoneOffset; // Xác định độ lệch múi giờ (ở đây dùng UTC+0)
import java.time.format.DateTimeParseException; // Bắt lỗi khi chuỗi thời gian sai định dạng
import java.util.Optional; // Bọc kết quả trả về để tránh lỗi NullPointerException

@Component
public class GpsTelemetryParser {

    // Danh sách các trường dùng để nhận diện bản tin này có chứa dữ liệu GPS hay không
    private static final String[] GPS_FIELDS = {
            "latitude",
            "longitude",
            "accuracyM",
            "speedKmh",
            "headingDeg",
            "measuredAt"
    };

    /**
     * Phân tích JSON Telemetry thành dữ liệu GPS.
     * Trả về Optional.empty() nếu bản tin không chứa GPS.
     * Ném IllegalArgumentException nếu dữ liệu GPS bị sai định dạng hoặc vượt ngưỡng.
     */
    public Optional<GpsTelemetryMessage> parse(JsonNode json) {

        // 1. Đảm bảo dữ liệu đầu vào phải là một JSON Object hợp lệ
        if (json == null || !json.isObject()) {
            throw new IllegalArgumentException("GPS telemetry must be a JSON object");
        }

        // 2. Nếu bản tin không có trường GPS nào thì bỏ qua (trả về rỗng)
        if (!hasGpsFields(json)) {
            return Optional.empty();
        }

        // 3. Đọc và kiểm tra mã thiết bị, mã bản tin
        String deviceCode = requiredString(json, "deviceId");
        String messageId = requiredString(json, "messageId");

        if (messageId.length() > 100) {
            throw new IllegalArgumentException("GPS messageId exceeds 100 characters");
        }

        // 4. Đọc và kiểm tra tọa độ vĩ độ, kinh độ bắt buộc
        BigDecimal latitude = requiredDecimal(json, "latitude");
        BigDecimal longitude = requiredDecimal(json, "longitude");

        validateCoordinate(latitude, "latitude", -90, 90);
        validateCoordinate(longitude, "longitude", -180, 180);

        // 5. Đọc các thông số phụ (không bắt buộc phải có trong JSON)
        BigDecimal accuracyM = optionalDecimal(json, "accuracyM");
        BigDecimal speedKmh = optionalDecimal(json, "speedKmh");
        BigDecimal headingDeg = optionalDecimal(json, "headingDeg");

        // Kiểm tra số chữ số để khớp với cấu hình cột DECIMAL(8,2) và DECIMAL(6,2) trong MariaDB
        validateOptionalDecimal(accuracyM, "accuracyM", 8, 2);
        validateOptionalDecimal(speedKmh, "speedKmh", 8, 2);
        validateOptionalDecimal(headingDeg, "headingDeg", 6, 2);

        // 6. Kiểm tra logic vật lý: sai số và vận tốc không được âm
        if (accuracyM != null && accuracyM.signum() < 0) {
            throw new IllegalArgumentException("Invalid GPS accuracyM");
        }

        if (speedKmh != null && speedKmh.signum() < 0) {
            throw new IllegalArgumentException("Invalid GPS speedKmh");
        }

        // Góc hướng di chuyển phải nằm trong khoảng [0, 360) độ
        if (headingDeg != null
                && (headingDeg.signum() < 0 || headingDeg.compareTo(BigDecimal.valueOf(360)) >= 0)) {
            throw new IllegalArgumentException("Invalid GPS headingDeg");
        }

        // 7. Đọc và chuyển đổi thời gian đo đạc sang chuẩn UTC
        LocalDateTime measuredAt = parseUtcTime(requiredString(json, "measuredAt"));

        // 8. Đóng gói toàn bộ dữ liệu sạch vào Record và trả về
        return Optional.of(
                new GpsTelemetryMessage(
                        deviceCode,
                        messageId,
                        latitude,
                        longitude,
                        accuracyM,
                        speedKmh,
                        headingDeg,
                        measuredAt
                )
        );
    }

    // Kiểm tra xem JSON có chứa ít nhất 1 trường GPS đặc trưng hay không
    private boolean hasGpsFields(JsonNode json) {
        for (String field : GPS_FIELDS) {
            if (json.has(field)) {
                return true;
            }
        }
        return false;
    }

    // Lấy giá trị chuỗi bắt buộc, báo lỗi nếu thiếu hoặc rỗng, tự động cắt khoảng trắng 2 đầu
    private String requiredString(JsonNode json, String field) {
        JsonNode node = json.get(field);

        if (node == null || !node.isString() || node.stringValue().isBlank()) {
            throw new IllegalArgumentException("Invalid or missing GPS field: " + field);
        }

        return node.stringValue().trim();
    }

    // Lấy giá trị số thập phân bắt buộc, báo lỗi nếu thiếu hoặc null
    private BigDecimal requiredDecimal(JsonNode json, String field) {
        BigDecimal value = optionalDecimal(json, field);

        if (value == null) {
            throw new IllegalArgumentException("Missing GPS field: " + field);
        }

        return value;
    }

    // Lấy giá trị số thập phân tùy chọn; chỉ chấp nhận kiểu số JSON (không ép kiểu từ String)
    private BigDecimal optionalDecimal(JsonNode json, String field) {
        JsonNode node = json.get(field);

        if (node == null || node.isNull()) {
            return null;
        }

        if (!node.isNumber()) {
            throw new IllegalArgumentException("GPS field must be a JSON number: " + field);
        }

        return new BigDecimal(node.toString());
    }

    // Kiểm tra giới hạn giá trị tọa độ [min, max] và tối đa 7 chữ số thập phân
    private void validateCoordinate(BigDecimal value, String field, int min, int max) {
        if (value.compareTo(BigDecimal.valueOf(min)) < 0
                || value.compareTo(BigDecimal.valueOf(max)) > 0) {
            throw new IllegalArgumentException("Invalid GPS coordinate: " + field);
        }

        // scale() trả về số chữ số sau dấu phẩy
        if (value.scale() > 7) {
            throw new IllegalArgumentException("GPS coordinate precision exceeds 7 decimals: " + field);
        }
    }

    // Kiểm tra tổng số chữ số (precision) và số chữ số lẻ (scale) để tránh lỗi khi lưu vào MariaDB
    private void validateOptionalDecimal(BigDecimal value, String field, int precision, int scale) {
        if (value == null) {
            return;
        }

        // Kiểm tra số chữ số lẻ và số chữ số phần nguyên (precision - scale)
        if (value.scale() > scale || value.precision() - value.scale() > precision - scale) {
            throw new IllegalArgumentException("Invalid GPS decimal precision: " + field);
        }
    }

    // Chuyển đổi chuỗi thời gian ISO 8601 (VD: "2026-11-16T15:30:00Z") sang LocalDateTime UTC
    private LocalDateTime parseUtcTime(String value) {
        // Bắt buộc ký tự cuối phải là 'Z' (Zulu time - chuẩn UTC)
        if (!value.endsWith("Z")) {
            throw new IllegalArgumentException("GPS measuredAt must use UTC Z");
        }

        try {
            Instant instant = Instant.parse(value);
            return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid GPS measuredAt format", e);
        }
    }
}

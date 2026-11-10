
package com.driveguard.backend.alert.service.Impl;

import com.driveguard.backend.alert.entity.AlertEntity;
import com.driveguard.backend.alert.enums.AlertSeverity;
import com.driveguard.backend.alert.enums.AlertStatus;
import com.driveguard.backend.alert.enums.AlertType;
import com.driveguard.backend.alert.repository.AlertRepository;
import com.driveguard.backend.alert.service.AlertService;
import com.driveguard.backend.alert.enums.AlertEpisodeStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.driveguard.backend.deviceevent.entity.DeviceEventEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.driveguard.backend.deviceevent.repository.DeviceEventRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertServiceImpl implements AlertService {

    private final AlertRepository alertRepository;
    private final DeviceEventRepository deviceEventRepository;

    private static final List<AlertStatus> ACTIVE_STATUSES =
            List.of(
                    AlertStatus.NEW,
                    AlertStatus.ACKNOWLEDGED
            );


    @Override
    @Transactional(readOnly = true)
    public Optional<AlertEntity> findActiveDrowsinessAlert(
            Long deviceId
    ) {

        if (deviceId == null) {
            throw new IllegalArgumentException(
                    "Device ID không được null"
            );
        }

        return alertRepository
                .findFirstByDeviceIdAndAlertTypeAndEpisodeStatusOrderByStartedAtDescIdDesc(
                        deviceId,
                        AlertType.DROWSINESS.name(),
                        AlertEpisodeStatus.ACTIVE
                );
    }

    @Override
    @Transactional
    public void endActiveDrowsinessEpisode(
            Long deviceId,  // ID của thiết bị giám sát hành trình cần kết thúc cảnh báo
            LocalDateTime endedAt  // Thời điểm xác định tài xế đã hết ngủ gật (từ bản tin gửi về)
    ) {

        // 1. Kiểm tra tính hợp lệ của Device ID (Không được null và phải lớn hơn 0)
        if (deviceId == null || deviceId <= 0) {
            throw new IllegalArgumentException(
                    "Device ID khong hop le"
            );
        }

        // 2. Kiểm tra thời điểm kết thúc (bắt buộc phải có dữ liệu thời gian)
        if (endedAt == null) {
            throw new IllegalArgumentException(
                    "Thoi diem ket thuc khong duoc null"
            );
        }

        // 3. Truy vấn DB để tìm đợt cảnh báo ngủ gật ĐẦU TIÊN thỏa mãn:
        // - Đúng ID thiết bị (deviceId)
        // - Đúng loại ngủ gật (DROWSINESS)
        // - Trạng thái đợt cảnh báo là đang hoạt động (ACTIVE)
        // - Sắp xếp theo thời gian bắt đầu giảm dần (Desc) và ID giảm dần (Desc) để lấy đợt mới nhất.
        Optional<AlertEntity> activeAlert =
                alertRepository
                        .findFirstByDeviceIdAndAlertTypeAndEpisodeStatusOrderByStartedAtDescIdDesc(
                                deviceId,
                                AlertType.DROWSINESS.name(),
                                AlertEpisodeStatus.ACTIVE
                        );

        // 4. Nếu KHÔNG tìm thấy đợt ngủ gật nào đang ACTIVE, ghi log debug và kết thúc hàm (không xử lý gì thêm)
        if (activeAlert.isEmpty()){
            log.debug(
                    "[ALERT] Khong co dot ngu gat ACTIVE | deviceId={}",
                    deviceId
            );
            return;
        }

        // Nếu tìm thấy, lấy đối tượng AlertEntity ra từ Optional
        AlertEntity alert = activeAlert.get();

        // 5. Cơ chế chống ghi đè dữ liệu cũ: Nếu thời điểm kết thúc (endedAt) nhận được
        // lại nằm TRƯỚC thời điểm bắt đầu đợt cảnh báo (startedAt) -> Đây là bản tin cũ bị trễ mạng gửi về sau.
        // Hệ thống sẽ ghi log cảnh báo (WARN) và bỏ qua, không cập nhật đợt cảnh báo hiện tại.
        if (endedAt.isBefore(alert.getStartedAt())) {
            log.warn(
                    "[ALERT] Bo qua NORMAL cu | alertId={} | startedAt={} | endedAt={}",
                    alert.getId(),
                    alert.getStartedAt(),
                    endedAt
            );
            return;
        }

        // 6. Nếu dữ liệu hợp lệ, tiến hành cập nhật trạng thái đợt cảnh báo thành ĐÃ KẾT THÚC (ENDED)
        alert.setEpisodeStatus(AlertEpisodeStatus.ENDED);
        // Cập nhật thời điểm kết thúc vào thực thể cảnh báo
        alert.setEndedAt(endedAt);

        // 7. Lưu lại các thay đổi của thực thể cảnh báo vào cơ sở dữ liệu
        alertRepository.save(alert);

        // 8. Ghi log thông báo (INFO) để theo dõi đợt ngủ gật này đã được đóng thành công
        log.info(
                "[ALERT] Ket thuc dot ngu gat | alertId={} | deviceId={} | endedAt={}",
                alert.getId(),
                deviceId,
                endedAt
        );
    }

    @Override
    @Transactional
    public AlertEntity createSosAlertIfAbsent(
            Long deviceId,
            String eventType,
            LocalDateTime startedAt
    ) {
        if (deviceId == null || deviceId <= 0) {
            throw new IllegalArgumentException("Invalid deviceId");
        }

        if (!AlertType.MANUAL_SOS.name().equals(eventType)
                && !AlertType.NO_RESPONSE_SOS.name().equals(eventType)) {

            throw new IllegalArgumentException(
                    "Unsupported SOS alert type: " + eventType
            );
        }

        List<AlertEntity> activeAlerts =
                alertRepository.findActiveSosAlerts(
                        deviceId,
                        AlertEpisodeStatus.ACTIVE
                );

        if (!activeAlerts.isEmpty()) {
            AlertEntity existing = activeAlerts.get(0);

            log.info(
                    "[SOS-ALERT] Existing ACTIVE alert | id={} | deviceId={}",
                    existing.getId(),
                    deviceId
            );

            return existing;
        }

        AlertEntity alert = new AlertEntity();

        alert.setDeviceId(deviceId);
        alert.setAlertType(eventType);
        alert.setSeverity(AlertSeverity.CRITICAL);
        alert.setStatus(AlertStatus.NEW);
        alert.setEpisodeStatus(AlertEpisodeStatus.ACTIVE);

        alert.setDescription(
                AlertType.MANUAL_SOS.name().equals(eventType)
                        ? "Tài xế kích hoạt nút SOS khẩn cấp."
                        : "Tài xế không phản hồi cảnh báo ngủ gật."
        );

        alert.setStartedAt(
                startedAt != null
                        ? startedAt
                        : LocalDateTime.now(java.time.ZoneOffset.UTC)
        );

        AlertEntity saved = alertRepository.save(alert);

        log.warn(
                "[SOS-ALERT] Created | id={} | deviceId={} | type={}",
                saved.getId(),
                deviceId,
                eventType
        );

        return saved;
    }

    @Override
    @Transactional
    public Optional<AlertEntity> acknowledgeActiveSosAlert(
            Long deviceId
    ) {
        if (deviceId == null || deviceId <= 0) {
            throw new IllegalArgumentException("Invalid deviceId");
        }

        List<AlertEntity> activeAlerts =
                alertRepository.findActiveSosAlerts(
                  deviceId,
                  AlertEpisodeStatus.ACTIVE
                );

        if (activeAlerts.isEmpty()) {
            log.warn(
                    "[SOS-ALERT] No ACTIVE alert to acknowledge | deviceId={}",
                    deviceId
            );
            return Optional.empty();
        }

        AlertEntity alert = activeAlerts.get(0);

        if (alert.getStatus() == AlertStatus.NEW) {
            alert.setStatus(AlertStatus.ACKNOWLEDGED);

            // Chua co tai khoan nguoi dung duoc xac thuc tu ESP32.
            // Giu ca hai truong NULL de tuan thu CHECK constraint.
            alert.setAcknowledgedBy(null);
            alert.setAcknowledgedAt(null);

            alert = alertRepository.save(alert);

            log.info(
                    "[SOS-ALERT] Acknowledged by device | id={} | deviceId={}",
                    alert.getId(),
                    deviceId
            );
        }

        return Optional.of(alert);
    }

    // Phương thức tạo cảnh báo SOS theo đợt
    @Override
    @Transactional // Quản lý giao dịch, nếu có lỗi xảy ra sẽ hủy toàn bộ các thao tác DB bên dưới
    public AlertEntity createSosAlertIfAbsent(
            Long deviceId,          // ID của thiết bị gửi tín hiệu
            String sosEpisodeId,    // Mã định danh duy nhất của đợt khẩn cấp SOS này
            String eventType,       // Loại sự kiện (MANUAL_SOS hoặc NO_RESPONSE_SOS)
            LocalDateTime startedAt // Thời gian bắt đầu đợt (nếu null sẽ lấy giờ UTC hiện tại)
    ) {
        // 1. Kiểm tra tính hợp lệ của ID thiết bị và mã đợt SOS (không được null/ rỗng)
        validateSosIdentity(deviceId,sosEpisodeId);

        // 2. Chặn các loại sự kiện khác, chỉ chấp nhận 2 loại sự kiện SOS được hỗ trợ
        if (!AlertType.MANUAL_SOS.name().equals(eventType)
                && !AlertType.NO_RESPONSE_SOS.name().equals(eventType)) {

            throw new IllegalArgumentException(
                    "Unsupported SOS alert type: " + eventType
            );
        }

        // 3. CHỐNG TRÙNG: Tìm xem đợt khẩn cấp (sosEpisodeId) của thiết bị này đã tồn tại trong DB chưa
        Optional<AlertEntity> existing =
                alertRepository.findByDeviceIdAndSosEpisodeId(
                        deviceId, sosEpisodeId
                );

        // Nếu ĐÃ TỒN TẠI đợt này -> Bỏ qua không tạo mới, trả về ngay bản ghi cũ đang có
        if (existing.isPresent()) {
            log.info(
                    "[SOS] Existing episode | alertId={} | episodeId={}",
                    existing.get().getId(),
                    sosEpisodeId
            );
            return existing.get();
        }


        // Kiểm tra sự kiện kết thúc của đợt SOS
        // đã được lưu trước khi nhận START hay chưa.

        Optional<DeviceEventEntity> endEvent =
                deviceEventRepository
                        .findFirstByDeviceIdAndSosEpisodeIdAndEventTypeOrderByReceivedAtAscIdAsc(
                                deviceId,
                                sosEpisodeId,
                                "SOS_ENDED"
                        );

        boolean hasEndEvent = endEvent.isPresent();

        if (hasEndEvent) {
            log.warn(
                    "[SOS] START arrived after SOS_ENDED | " +
                            "deviceId={} | episodeId={} | endReceivedAt={}",
                    deviceId,
                    sosEpisodeId,
                    endEvent.get().getReceivedAt()
            );
        }

        // 4. Nếu CHƯA TỒN TẠI -> Khởi tạo một đối tượng thực thể cảnh báo SOS mới
        AlertEntity alert = new AlertEntity();
        alert.setDeviceId(deviceId);
        alert.setSosEpisodeId(sosEpisodeId); // Gắn mã đợt khẩn cấp phục vụ cho việc đối chiếu sau này
        alert.setAlertType(eventType);
        alert.setSeverity(AlertSeverity.CRITICAL);  // Mức độ nghiêm trọng: Nguy hiểm/ Nguy kịch
        alert.setStatus(AlertStatus.NEW);

        alert.setEpisodeStatus(
                hasEndEvent
                        ? AlertEpisodeStatus.ENDED
                        : AlertEpisodeStatus.ACTIVE
        );

        // Tự động gán nội dung mô tả dựa vào loại sự kiện tương ứng
        alert.setDescription(
                AlertType.MANUAL_SOS.name().equals(eventType)
                        ? "Tài xế kích hoạt nút SOS khẩn cấp."
                        : "Tài xế không phản hồi cảnh báo ngủ gật."
        );

        // Lưu thời gian bắt đầu (Ưu tiên giờ từ thiết bị gửi lên, nếu không có thì lấy giờ hệ thống dạng UTC)
        alert.setStartedAt(
                startedAt != null
                        ? startedAt
                        : LocalDateTime.now(java.time.ZoneOffset.UTC)
        );



        if (hasEndEvent) {
            // END đã được lưu trước khi START được xử lý.
            // Không thể suy ra thời điểm kết thúc thực tế chỉ
            // từ thời gian Backend tiếp nhận MQTT.

            alert.setEndedAt(null);

            log.warn(
                    "[SOS] Out-of-order episode marked ENDED " +
                            "with unknown endedAt | deviceId={} | episodeId={}",
                    deviceId,
                    sosEpisodeId
            );
        }

        // 5. Lưu cảnh báo SOS mới này xuống cơ sở dữ liệu
        AlertEntity saved = alertRepository.save(alert);

        // Ghi log mức WARN nhằm thông báo cho đội vận hành về sự cố khẩn cấp vừa phát sinh
        log.warn(
                "[SOS] Created | alertId={} | deviceId={} | episodeId={}",
                saved.getId(),
                deviceId,
                sosEpisodeId
        );

        return saved;
    }

    // Thêm phương thức xác nhận SOS
    @Override
    @Transactional // Quản lý giao dịch, tự động rollback (hủy thay đổi) nếu xảy ra lỗi trong DB
    public Optional<AlertEntity> acknowledgeSosAlert(
            Long deviceId,      // ID của thiết bị gửi tín hiệu xác nhận
            String sosEpisodeId // Mã định danh duy nhất của đợt khẩn cấp SOS cần xác nhận
    ) {
        // 1F. Kiểm tra tính hợp lệ của ID thiết bị và mã đợt SOS (Không được null hoặc trống)
        validateSosIdentity(deviceId, sosEpisodeId);

        // 2. Tìm đợt cảnh báo khẩn cấp tương ứng trong cơ sở dữ liệu
        Optional<AlertEntity> existing =
                alertRepository.findByDeviceIdAndSosEpisodeId(
                        deviceId, sosEpisodeId
                );

        // Nếu KHÔNG tìm thấy đợt SOS này -> Ghi log cảnh báo (WARN) và trả về kết quả trống (Option.empty)
        if (existing.isEmpty()){
            log.warn(
                    "[SOS] Acknowledgement without episode | deviceId={} | episodeId={}",
                    deviceId,
                    sosEpisodeId
            );
            return Optional.empty();
        }

        // Nếu tìm thấy, lấy đối tượng thực thể cảnh báo ra để xử lý
        AlertEntity alert = existing.get();

        // 3. Nếu đợt cảnh báo SOS này đã kết thúc (ENDED) -> Bỏ qua không cập nhật và trả về dữ liệu hiện tại
        if (alert.getEpisodeStatus() == AlertEpisodeStatus.ENDED){
            log.info(
                    "[SOS] Ignored acknowledgement for ENDED episode | alertId={}",
                    alert.getId()
            );
            return Optional.of(alert);
        }

        // 4. Nếu cảnh báo đang ở trạng thái mới tạo (NEW) -> Tiến hành cập nhật trạng thái xác nhận
        if (alert.getStatus() == AlertStatus.NEW){
            alert.setStatus(AlertStatus.ACKNOWLEDGED); // Đổi trạng thái xử lý thành: Đã ghi nhận

            // Tín hiệu từ phần cứng gửi lên (Không phải tài khoản người dùng đăng nhập bấm nút)
            // Giữ nguyên giá trị NULL cho 2 trường này để tuân thủ ràng buộc CHECK constraint của DB
            alert.setAcknowledgedBy(null);
            alert.setAcknowledgedAt(null);

            // 5. Lưu lại thay đổi vào cơ sở dữ liệu
            alert = alertRepository.save(alert);

            // Ghi log thông báo thiết bị đã xác nhận thành công đợt SOS này
            log.info(
                    "[SOS] Device acknowledged | alertId={} | episodeId={}",
                    alert.getId(),
                    sosEpisodeId
            );
        }
        // Trả về đối tượng cảnh báo sau khi xử lý bọc trong Optional
        return Optional.of(alert);
    }

    @Override
    @Transactional // Đảm bảo tính toàn vẹn dữ liệu, nếu ghi DB lỗi sẽ tự động rollback (hủy thay đổi)
    public Optional<AlertEntity> endSosEpisode(
            Long deviceId,         // ID của thiết bị gửi tín hiệu kết thúc
            String sosEpisodeId,   // Mã định danh duy nhất của đợt khẩn cấp SOS cần đóng
            LocalDateTime endedAt  // Thời điểm kết thúc đợt khẩn cấp SOS
    ) {
        // 1. Kiểm tra tính hợp lệ của ID thiết bị và mã đợt SOS (Không được null hoặc rỗng)
        validateSosIdentity(deviceId, sosEpisodeId);

        // 2. Bắt buộc phải có thời điểm kết thúc đợt khẩn cấp
        if (endedAt == null) {
            throw new IllegalArgumentException(
                    "SOS endedAt must not be null"
            );
        }
        // 3. Truy vấn DB để tìm đợt cảnh báo khẩn cấp tương ứng theo thiết bị và mã đợt
        Optional<AlertEntity> existing =
                alertRepository.findByDeviceIdAndSosEpisodeId(
                        deviceId, sosEpisodeId
                );

        // Nếu KHÔNG tìm thấy đợt SOS này -> Ghi log cảnh báo (WARN) và trả về kết quả trống (Optional.empty)
        if (existing.isEmpty()){
            log.warn(
                    "[SOS] End event without episode | deviceId={} | episodeId={}",
                    deviceId,
                    sosEpisodeId
            );
            return Optional.empty();
        }

        // Nếu tìm thấy, lấy đối tượng thực thể cảnh báo ra để xử lý
        AlertEntity alert = existing.get();

        // 4. Nếu đợt cảnh báo SOS này ĐÃ KẾT THÚC từ trước -> Bỏ qua không xử lý lại, trả về dữ liệu hiện tại
        if (alert.getEpisodeStatus() == AlertEpisodeStatus.ENDED){
            log.info(
                    "[SOS] Episode already ENDED | alertId={}",
                    alert.getId()
            );
            return Optional.of(alert);
        }

        // 5. Kiểm tra logic thời gian: Nếu thời điểm kết thúc lại TRƯỚC thời điểm bắt đầu đợt cảnh báo
        // -> Bản tin bị lỗi hoặc gửi trễ do mạng. Ghi log cảnh báo và không cập nhật trạng thái.

        if (endedAt.isBefore(alert.getStartedAt())) {

            // Thời gian Backend tiếp nhận END nhỏ hơn START.
            // Không thể dùng hai mốc tiếp nhận để kết luận
            // rằng sự kiện END không hợp lệ.

            log.warn(
                    "[SOS] END received before START timestamp | " +
                            "alertId={} | episodeId={} | startedAt={} | endReceivedAt={}",
                    alert.getId(),
                    sosEpisodeId,
                    alert.getStartedAt(),
                    endedAt
            );

            // Episode được xác định là đã kết thúc.
            alert.setEpisodeStatus(AlertEpisodeStatus.ENDED);

            // Chưa xác định được thời gian kết thúc thực tế.
            alert.setEndedAt(null);

            AlertEntity saved = alertRepository.save(alert);

            return Optional.of(saved);
        }


        // 6. Nếu mọi điều kiện hợp lệ, cập nhật trạng thái đợt cảnh báo thành ĐÃ KẾT THÚC (ENDED)
        alert.setEpisodeStatus(AlertEpisodeStatus.ENDED);
        alert.setEndedAt(endedAt); // Lưu lại mốc thời gian kết thúc

        // 7. Lưu lại các thay đổi vào cơ sở dữ liệu
        AlertEntity saved = alertRepository.save(alert);

        // Ghi log thông báo đợt khẩn cấp SOS đã được đóng thành công (Mức INFO)
        log.info(
                "[SOS] Episode ENDED | alertId={} | episodeId={}",
                saved.getId(),
                sosEpisodeId
        );

        // Trả về thực thể sau khi đã cập nhật thành công bọc trong Optional
        return Optional.of(saved);
    }


    @Override
    @Transactional
    public AlertEntity createDrowsinessAlertIfAbsent(
            Long deviceId,
            Long driverId,
            Long readingId,
            LocalDateTime startedAt
    ) {

        if (deviceId == null) {
            throw new IllegalArgumentException(
                    "Device ID không được null"
            );
        }

        if (readingId == null) {
            throw new IllegalArgumentException(
                    "Reading ID không được null"
            );
        }

        Optional<AlertEntity> existingAlert =
                findActiveDrowsinessAlert(deviceId);

        if (existingAlert.isPresent()) {

            log.info(
                    "[ALERT] Cảnh báo DROWSINESS đang tồn tại | " +
                            "deviceId={} | alertId={}",
                    deviceId,
                    existingAlert.get().getId()
            );

            return existingAlert.get();
        }

        AlertEntity alert = new AlertEntity();

        alert.setDeviceId(deviceId);
        alert.setDriverId(driverId);
        alert.setReadingId(readingId);

        alert.setAlertType(AlertType.DROWSINESS.name());
        alert.setSeverity(AlertSeverity.MEDIUM);
        alert.setStatus(AlertStatus.NEW);
        alert.setEpisodeStatus(AlertEpisodeStatus.ACTIVE);

        alert.setDescription(
                "Phát hiện dấu hiệu ngủ gật: " +
                        "tài xế nhắm mắt liên tục từ 3 giây trở lên."
        );

        alert.setStartedAt(
                startedAt != null
                        ? startedAt
                        : LocalDateTime.now(java.time.ZoneOffset.UTC)
        );

        AlertEntity savedAlert = alertRepository.save(alert);

        log.warn(
                "[ALERT] Đã tạo cảnh báo ngủ gật | " +
                        "alertId={} | deviceId={} | readingId={} | severity={}",
                savedAlert.getId(),
                savedAlert.getDeviceId(),
                savedAlert.getReadingId(),
                savedAlert.getSeverity()
        );

        return savedAlert;
    }

    // Hàm bổ trợ kiểm tra tính hợp lệ và định dạng an toàn của ID thiết bị
    private void validateSosIdentity(
            Long deviceId,       // ID của thiết bị cần kiểm tra
            String sosEpisodeId  // Mã định danh đợt khẩn cấp SOS cần kiểm tra
    ){
      // 1. Kiểm tra ID thiết bị: Không được null và bắt buộc phải là số dương lớn hơn 0
      if (deviceId == null || deviceId <= 0) {
          throw new IllegalArgumentException(
                  "Invalid deviceId"
          );
      }

        // 2. Kiểm tra mã đợt SOS: Không được phép null và không được chỉ chứa toàn dấu cách (rỗng)
        if (sosEpisodeId == null || sosEpisodeId.isBlank()) {
            throw new IllegalArgumentException(
                    "sosEpisodeId must not be blank"
            );
        }

        // 3. Kiểm tra định dạng nâng cao:
        // - Độ dài chuỗi không được vượt quá 150 ký tự (tránh lỗi tràn cột DB)
        // - Chuỗi gốc phải trùng khít với chuỗi sau khi đã trim (nghĩa là cấm chứa khoảng trắng thừa ở đầu hoặc cuối)
        if (sosEpisodeId.length() > 150
                || !sosEpisodeId.equals(sosEpisodeId.trim())) {
            throw new IllegalArgumentException(
                    "Invalid sosEpisodeId format or length"
            );
        }
    }
}

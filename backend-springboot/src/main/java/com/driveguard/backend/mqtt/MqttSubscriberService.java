
package com.driveguard.backend.mqtt;

import com.driveguard.backend.deviceevent.exception.DeviceNotFoundException;

import com.driveguard.backend.device.service.DeviceService;
import com.driveguard.backend.sensor.service.SensorReadingService;

import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.driveguard.backend.deviceevent.dto.mqtt.DeviceEventMessage;
import com.driveguard.backend.deviceevent.service.DeviceEventService;
import com.driveguard.backend.mqtt.service.MqttDeadLetterService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * @Service: Đánh dấu đây là một Bean tầng Service quản lý bởi Spring Container.
 * Spring sẽ tự động phát hiện, khởi tạo và quản lý vòng đời của Class này.
 */
@Slf4j
@Service
@ConditionalOnProperty( // để bật hoặc tắt MQTT Subscriber
        prefix = "driveguard.mqtt",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
@RequiredArgsConstructor
public class MqttSubscriberService {

    @Value("${driveguard.mqtt.broker-url}")
    private String mqttBroker;

    @Value("${driveguard.mqtt.telemetry-topic}")
    private String mqttTelemetryTopic;

    @Value("${driveguard.mqtt.events-topic}")
    private String mqttEventsTopic;

    @Value("${driveguard.mqtt.client-id:driveguard-backend}")
    private String mqttClientId;


    // Service xử lý nghiệp vụ thiết bị (Interface), Spring inject DeviceServiceImpl
    private final DeviceService deviceService;

    // Service xử lý và lưu dữ liệu cảm biến vào bảng sensor_readings
    // Spring tự động inject SensorReadingServiceImpl
    private final SensorReadingService sensorReadingService;

    // Service tiếp nhận, xác thực và lưu sự kiện ESP32-S3.
    private final DeviceEventService deviceEventService;
    private final MqttDeadLetterService mqttDeadLetterService;

    // ObjectMapper dùng để chuyển đổi dữ liệu JSON từ MQTT thành đối tượng Java
    private final ObjectMapper objectMapper;

    // Đối tượng Client của thư viện Paho MQTT dùng để kết nối và tương tác với Broker
    private MqttAsyncClient mqttClient;

    /**
     * @PostConstruct: Định nghĩa hàm này sẽ TỰ ĐỘNG CHẠY ngay sau khi
     * Spring Boot khởi tạo xong Service này (vừa bật App lên là kết nối MQTT ngay).
     */
    @PostConstruct
    public void connect() {
        try {
            // Tạo một Client ID ngẫu nhiên bằng UUID để tránh bị trùng lặp trên Broker
            // (Nếu 2 client trùng ID kết nối vào Broker, cái cũ sẽ bị ngắt kết nối)

            String clientId = mqttClientId;

            // Lưu trạng thái MQTT trong RAM, không tạo thư mục persistence.
            // V9.4.9: Lưu trạng thái MQTT Client xuống ổ đĩa.
            // Đường dẫn này cần được duy trì qua các lần khởi động Backend.
            String persistenceDirectory = "./mqtt-persistence";

            mqttClient = new MqttAsyncClient(
                    mqttBroker,
                    clientId,
                    new MqttDefaultFilePersistence(persistenceDirectory)
            );

            // V9.4.9: Cho phép Backend chủ động xác nhận bản tin MQTT.
            // Không tự động ACK khi callback messageArrived() kết thúc.
            mqttClient.setManualAcks(true);

            // Cấu hình các tùy chọn khi kết nối
            // Cấu hình MQTT
            MqttConnectOptions options = new MqttConnectOptions();

            // Tự động kết nối lại sau khi mất kết nối
            options.setAutomaticReconnect(true);

            // Không duy trì phiên MQTT cũ
            options.setCleanSession(false);

            // Thời gian chờ thiết lập kết nối
            options.setConnectionTimeout(10);

            // Khoảng thời gian keep-alive MQTT (giây)
            options.setKeepAliveInterval(30);
            // Cài đặt các hàm Callback (sự kiện) để xử lý khi nó tương tác mạng
            mqttClient.setCallback(new MqttCallbackExtended() {

                /**
                 * Sự kiện bổ sung: Chạy khi kết nối MQTT thành công hoặc kết nối lại.
                 * Vì cleanSession = true, cần subscribe lại sau khi reconnect.
                 */

                @Override
                public void connectComplete(
                        boolean reconnect,
                        String serverURI
                ) {
                    try {
                        String[] topics = {
                                mqttTelemetryTopic,
                                mqttEventsTopic
                        };

                        int[] qos = {
                                0,
                                1
                        };

                        // V9.4.9.4: Subscribe bất đồng bộ.
                        // Không dùng waitForCompletion() bên trong MQTT callback.
                        mqttClient.subscribe(
                                topics,
                                qos,
                                null,
                                new IMqttActionListener() {

                                    @Override
                                    public void onSuccess(IMqttToken token) {
                                        log.info(
                                                "[MQTT] Subscribe thanh cong | telemetry={} | events={}",
                                                mqttTelemetryTopic,
                                                mqttEventsTopic
                                        );
                                    }

                                    @Override
                                    public void onFailure(IMqttToken token, Throwable exception) {
                                        log.error(
                                                "[MQTT] Subscribe that bai",
                                                exception
                                        );
                                    }
                                }
                        );

                        log.info(
                                "[MQTT] {} broker thanh cong: {}",
                                reconnect ? "Tai ket noi" : "Ket noi",
                                serverURI
                        );

                        log.info(
                                "[MQTT] Subscribed telemetry: {}",
                                mqttTelemetryTopic
                        );

                        log.info(
                                "[MQTT] Subscribed events: {}",
                                mqttEventsTopic
                        );

                    } catch (MqttException e) {
                        log.error(
                                "[MQTT] Loi subscribe topics",
                                e
                        );
                    }
                }


                /**
                 * Sự kiện 1: Chạy khi kết nối giữa Spring Boot và Broker bị ngắt đột ngột
                 */
                @Override
                public void connectionLost(Throwable cause) {
                    log.warn(
                            "[MQTT] Mat ket noi: {}",
                            cause != null ? cause.getMessage() : "Khong ro nguyen nhan"
                    );
                }

                /**
                 * Sự kiện 2: QUAN TRỌNG NHẤT - Chạy bất cứ khi nào có tin nhắn mới
                 * từ Topic "driveguard/demo/esp32s3/telemetry" gửi về.
                 */
                @Override
                public void messageArrived(
                        String topic,
                        MqttMessage message
                ) throws Exception {
                    log.info("[MQTT DEBUG] messageArrived() duoc goi!");
                    log.info("[MQTT DEBUG] Topic = {}", topic);
                    log.info("[MQTT DEBUG] Payload = {}", message.toString());
                    // Chuyển mảng Byte nhận được từ tin nhắn sang chuỗi String (định dạng UTF-8)
                    String payload = new String(
                            message.getPayload(),
                            StandardCharsets.UTF_8
                    );


                    // Phân luồng theo MQTT topic trước khi xử lý JSON.

                    if (mqttEventsTopic.equals(topic)) {

                        try {

                            // Chỉ ACK sau khi saveEvent() hoàn tất thành công.
                            handleEventMessage(payload);

                            if (message.getQos() > 0) {

                                mqttClient.messageArrivedComplete(
                                        message.getId(),
                                        message.getQos()
                                );

                                log.info(
                                        "[MQTT-ACK] Event acknowledged | messageId={} | qos={}",
                                        message.getId(),
                                        message.getQos()
                                );
                            }

                        } catch (DeviceNotFoundException e) {
                            Long deadLetterId = mqttDeadLetterService.saveInvalidMessage(
                                    topic,
                                    message.getPayload(),
                                    "DEVICE_NOT_FOUND",
                                    e.getMessage()
                            );

                            if (message.getQos() > 0) {
                                mqttClient.messageArrivedComplete(
                                        message.getId(),
                                        message.getQos()
                                );
                            }

                            log.warn(
                                    "[MQTT-DLQ] Unknown device stored and ACKed | dlqId={} | messageId={}",
                                    deadLetterId,
                                    message.getId()
                            );

                        } catch (InvalidMqttEventException e) {
                            mqttDeadLetterService.saveInvalidMessage(
                                    topic,
                                    message.getPayload(),
                                    "INVALID_JSON_SYNTAX",
                                    e.getMessage()
                            );

                            if (message.getQos() > 0) {
                                mqttClient.messageArrivedComplete(
                                        message.getId(),
                                        message.getQos()
                                );
                            }

                            log.warn(
                                    "[MQTT-DLQ] Invalid JSON stored and ACKed | messageId={}",
                                    message.getId()
                            );

                        } catch (Exception e) {

                            log.error(
                                    "[MQTT-ACK] Event processing failed | messageId={} | qos={} | topic={}",
                                    message.getId(),
                                    message.getQos(),
                                    topic,
                                    e
                            );

                            // Không ACK khi xử lý thất bại.
                            // Chuyển ngoại lệ cho Paho xử lý trạng thái kết nối.
                            throw e;
                        }

                        return;
                    }

                    if (!mqttTelemetryTopic.equals(topic)) {
                        log.warn(
                                "[MQTT] Bo qua topic khong ho tro: {}",
                                topic
                        );
                        return;
                    }


                    // In ra màn hình Console để lập trình viên theo dõi
                    log.info("[MQTT] Topic: {}", topic);
                    log.info("[MQTT] Payload: {}", payload);

                    // hoặc xử lý dữ liệu cảnh báo lái xe ở đây.

                    try {
                        // Chuyển chuỗi JSON thành JsonNode
                        JsonNode json = objectMapper.readTree(payload);

                        // Kiểm tra dữ liệu phải là một JSON Object
                        if (!json.isObject()) {
                            log.warn(
                                    "[MQTT] Payload khong phai JSON Object"
                            );
                            return;
                        }

                        // Lấy mã thiết bị từ trường deviceId trong JSON
                        JsonNode deviceIdNode = json.get("deviceId");

                        // Kiểm tra deviceId có tồn tại và hợp lệ không
                        if (deviceIdNode == null
                                || !deviceIdNode.isString()
                                || deviceIdNode.stringValue().isBlank()) {

                            log.warn(
                                    "[MQTT] Thieu deviceId hoac deviceId khong hop le"
                            );
                            return;
                        }

                        String deviceCode = deviceIdNode.stringValue().trim();

                        // Kiểm tra trạng thái ONLINE trong payload nếu có
                        JsonNode statusNode = json.get("status");

                        if (statusNode != null
                                && (!statusNode.isString()
                                || !"ONLINE".equals(statusNode.stringValue()))) {

                            log.warn(
                                    "[MQTT] Bo qua trang thai khong phai ONLINE: {}",
                                    statusNode.toString()
                            );
                            return;
                        }

                        // Gọi tầng Service để tìm thiết bị và cập nhật MariaDB
                        deviceService.markDeviceOnline(deviceCode);

                        log.info(
                                "[MQTT] Xu ly thiet bi thanh cong: {}",
                                deviceCode
                        );

                        // ==============================================
                        // BƯỚC MỚI: XỬ LÝ DỮ LIỆU CẢM BIẾN TỪ MQTT
                        // ==============================================

                        // Kiểm tra bản tin có chứa dữ liệu cảm biến hay không.
                        // Các bản tin chỉ chứa trạng thái ONLINE vẫn được chấp nhận.
                        JsonNode heartRateNode = json.get("heartRate");
                        JsonNode spo2Node = json.get("spo2");
                        JsonNode edgeAiStatusNode = json.get("edgeAiStatus");
                        JsonNode isDrowsyNode = json.get("isDrowsy");

                        boolean hasSensorData =
                                (heartRateNode != null && !heartRateNode.isNull())
                                        || (spo2Node != null && !spo2Node.isNull())
                                        || (edgeAiStatusNode != null && !edgeAiStatusNode.isNull())
                                        || (isDrowsyNode != null && !isDrowsyNode.isNull());

                        if (!hasSensorData) {
                            log.info(
                                    "[SENSOR] Ban tin chi cap nhat trang thai thiet bi: {}",
                                    deviceCode
                            );
                            return;
                        }

                        // Chuyển giá trị cảm biến thành BigDecimal.
                        // Nếu trường không có hoặc là JSON null thì trả về null.
                        // Nếu trường có nhưng không phải số thì từ chối bản tin.
                        BigDecimal heartRate = readDecimal(json, "heartRate");
                        BigDecimal spo2 = readDecimal(json, "spo2");
                        BigDecimal edgeAiConfidence =
                                readDecimal(json, "edgeAiConfidence");

                        // Đọc trạng thái AI mô phỏng.
                        String edgeAiStatus =
                                readString(json, "edgeAiStatus");

                        // Đọc kết quả phát hiện ngủ gật.
                        Boolean isDrowsy =
                                readBoolean(json, "isDrowsy");

                        // Firmware Wokwi hiện chưa gửi measuredAt.
                        // Tạm dùng thời điểm backend xử lý bản tin theo UTC.
                        // Sau này sẽ thay bằng thời điểm đo từ ESP32-S3.
                        LocalDateTime measuredAt =
                                LocalDateTime.now(ZoneOffset.UTC);

                        // Gọi tầng Service để kiểm tra dữ liệu
                        // và lưu vào bảng sensor_readings trong MariaDB.
                        var savedReading = sensorReadingService.saveReading(
                                deviceCode,
                                measuredAt,
                                heartRate,
                                spo2,
                                edgeAiStatus,
                                edgeAiConfidence,
                                isDrowsy,
                                payload
                        );

                        log.info(
                                "[SENSOR] Luu du lieu thanh cong | ID: {} | Device: {}",
                                savedReading.getId(),
                                deviceCode
                        );

                    } catch (Exception e) {
                        // Xử lý lỗi JSON, thiết bị không tồn tại hoặc lỗi database
                        log.error(
                                "[MQTT] Loi xu ly du lieu MQTT: {}",
                                payload,
                                e
                        );
                    }
                }

                /**
                 * Sự kiện 3: Chỉ dùng khi Spring Boot gửi (Publish) tin nhắn đi.
                 * Ở đây ta chỉ lắng nghe (Subscriber) nên hàm này bỏ trống.
                 */
                @Override
                public void deliveryComplete(
                        IMqttDeliveryToken token
                ) {
                    // Subscriber khong can xu ly publish callback
                }
            });

            // Tiến hành gửi lệnh kết nối thực tế tới MQTT Broker
            // V9.4.9.4: Kết nối bằng MqttAsyncClient.
            IMqttToken connectToken = mqttClient.connect(options);

            // Chờ hoàn thành kết nối ban đầu.
            // Lệnh này nằm trong @PostConstruct, không nằm trong MQTT callback.
            connectToken.waitForCompletion(15000);

            log.info(
                    "[MQTT] Ket noi ban dau thanh cong | clientId={}",
                    mqttClientId
            );

            // Đăng ký (Subscribe) lắng nghe vào Topic cụ thể với mức độ chất lượng dịch vụ QoS = 0
            // Đã chuyển thao tác subscribe vào connectComplete()
            // để tự động subscribe lại sau khi mất kết nối.

            // Thông báo kết nối và đăng ký Topic thành công lên Console
            // Đã chuyển thông báo vào connectComplete() để ghi log cho cả lần kết nối đầu và reconnect.

        } catch (MqttException e) {
            // In ra lỗi nếu quá trình kết nối hoặc đăng ký Topic thất bại
            log.error("[MQTT] Loi ket noi: {}", e.getMessage(), e);
        }
    }

    /**
     * Đọc trường số từ JSON và chuyển thành BigDecimal.
     *
     * Không tự động chuyển chuỗi thành số nhằm tránh
     * chấp nhận dữ liệu sai định dạng từ MQTT.
     */
    private BigDecimal readDecimal(JsonNode json, String fieldName) {
        JsonNode node = json.get(fieldName);

        if (node == null || node.isNull()) {
            return null;
        }

        if (!node.isNumber()) {
            throw new IllegalArgumentException(
                    "Truong " + fieldName + " phai la so"
            );
        }

        return new BigDecimal(node.toString());
    }


    /**
     * Xử lý bản tin MQTT Events từ ESP32-S3.
     *
     * Topic:
     * driveguard/demo/esp32s3/events
     *
     * Chức năng:
     * 1. Kiểm tra JSON Object.
     * 2. Chuyển JSON thành DeviceEventMessage.
     * 3. Gọi DeviceEventService để xác thực và lưu MariaDB.
     *
     * DeviceEventService xử lý lưu sự kiện,
     * đồng thời chuyển các sự kiện SOS đến AlertService
     * để tạo, xác nhận hoặc kết thúc đợt SOS.
     */

    private static class InvalidMqttEventException
            extends IllegalArgumentException {

        private InvalidMqttEventException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    private void handleEventMessage(String payload) {

        try {
            // Giới hạn kích thước trước khi parse JSON.
            if (payload.getBytes(StandardCharsets.UTF_8).length
                    > 16 * 1024) {

                throw new IllegalArgumentException(
                        "MQTT event payload exceeds 16 KB"
                );
            }

            JsonNode json;
            try {
                json = objectMapper.readTree(payload);
            } catch (tools.jackson.core.JacksonException e) {
                throw new InvalidMqttEventException(
                        "Invalid MQTT event JSON syntax",
                        e
                );
            }

            if (json == null || !json.isObject()) {

                throw new IllegalArgumentException(
                        "MQTT event payload must be a JSON Object"
                );
            }

            DeviceEventMessage event = new DeviceEventMessage();

            // Mã định danh duy nhất của từng sự kiện MQTT
            event.setEventId(
                    readString(json, "eventId")
            );

            // Mã thiết bị ESP32-S3
            event.setDeviceId(
                    readString(json, "deviceId")
            );

            // Loại sự kiện: MANUAL_SOS, SOS_ENDED, ...
            event.setEventType(
                    readString(json, "eventType")
            );

            // QUAN TRỌNG: Định danh đợt SOS
            // Giúp START, ACK, MUTE, END liên kết đúng một episode
            event.setSosEpisodeId(
                    readString(json, "sosEpisodeId")
            );

            // Định danh lần khởi động của ESP32-S3
            event.setBootId(
                    readString(json, "bootId")
            );

            // Số thứ tự sự kiện
            event.setSequence(
                    readLong(json, "sequence")
            );

            // Thời gian hoạt động của ESP32-S3, tính bằng milliseconds
            event.setTimestampMs(
                    readLong(json, "timestampMs")
            );

            var saved = deviceEventService.saveEvent(
                    event,
                    payload
            );

            log.info(
                    "[MQTT-EVENT] Processed | id={} | eventId={} | type={}",
                    saved.getId(),
                    saved.getEventId(),
                    saved.getEventType()
            );

        } catch (IllegalArgumentException e) {

            log.warn(
                    "[MQTT-EVENT] Invalid event: {}",
                    e.getMessage()
            );

            // V9.4.9: Giữ lại thông tin lỗi để tầng MQTT
            // có thể quyết định chính sách xử lý bản tin.
            throw e;

        } catch (Exception e) {

            log.error(
                    "[MQTT-EVENT] Failed to process event",
                    e
            );

            // V9.4.9: Không được coi lỗi database
            // là một lần xử lý MQTT thành công.
            throw new IllegalStateException(
                    "Failed to process MQTT event",
                    e
            );
        }
    }

    /**
     * Đọc một số nguyên Long từ JSON.
     *
     * Không chấp nhận:
     * - Số thập phân
     * - Chuỗi số
     * - Boolean
     * - Số vượt phạm vi Long
     */
    private Long readLong(
            JsonNode json,
            String fieldName
    ) {
        JsonNode node = json.get(fieldName);

        if (node == null || node.isNull()) {
            return null;
        }

        if (!node.isIntegralNumber() || !node.canConvertToLong()) {
            throw new IllegalArgumentException(
                    "Field " + fieldName
                            + " must be a valid Long integer"
            );
        }

        return node.longValue();
    }


    /**
     * Đọc trường chuỗi từ JSON.
     *
     * Nếu không có trường hoặc giá trị null,
     * trả về null để phù hợp với Entity.
     */
    private String readString(JsonNode json, String fieldName) {
        JsonNode node = json.get(fieldName);

        if (node == null || node.isNull()) {
            return null;
        }

        if (!node.isString()) {
            throw new IllegalArgumentException(
                    "Truong " + fieldName + " phai la chuoi"
            );
        }

        return node.stringValue();
    }

    /**
     * Đọc trường Boolean từ JSON.
     *
     * Chỉ chấp nhận true hoặc false thực sự,
     * không chấp nhận chuỗi "true" hay "false".
     */
    private Boolean readBoolean(JsonNode json, String fieldName) {
        JsonNode node = json.get(fieldName);

        if (node == null || node.isNull()) {
            return null;
        }

        if (!node.isBoolean()) {
            throw new IllegalArgumentException(
                    "Truong " + fieldName + " phai la Boolean"
            );
        }

        return node.booleanValue();
    }

    /**
     * @PreDestroy: Định nghĩa hàm này sẽ TỰ ĐỘNG CHẠY ngay trước khi
     * ứng dụng Spring Boot bị tắt hoặc tắt server (để dọn dẹp kết nối).
     */
    @PreDestroy
    public void disconnect() {

        if (mqttClient == null) {
            return;
        }

        try {

            if (mqttClient.isConnected()) {

                // V9.4.9.5: Ngắt kết nối MQTT bất đồng bộ.
                IMqttToken disconnectToken = mqttClient.disconnect();

                // Chờ ngắt kết nối hoàn tất.
                disconnectToken.waitForCompletion(10000);

                log.info("[MQTT] Disconnect thanh cong");
            }

        } catch (MqttException e) {

            log.error(
                    "[MQTT] Loi khi disconnect MQTT",
                    e
            );

        } finally {

            try {
                mqttClient.close();
                log.info("[MQTT] Da dong MQTT Client");

            } catch (MqttException e) {
                log.error(
                        "[MQTT] Loi khi dong MQTT Client",
                        e
                );
            }
        }
    }
}

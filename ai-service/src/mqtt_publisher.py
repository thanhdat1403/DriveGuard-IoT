"""Gửi Telemetry AI từ DriveGuard đến MQTT Broker."""

import json
import time
import uuid

import paho.mqtt.client as mqtt


class DriveGuardMqttPublisher:
    def __init__(
        self,
        host="127.0.0.1",
        port=1884,
        topic="driveguard/e2e/esp32s3/telemetry",
        device_id="ESP32S3-E2E-001",
    ):
        self.host = host
        self.port = port
        self.topic = topic
        self.device_id = device_id
        self.connected = False
        self.client = mqtt.Client(
            callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
            client_id=f"driveguard-ai-{uuid.uuid4().hex[:12]}",
            protocol=mqtt.MQTTv311,
        )
        self.client.on_connect = self._on_connect
        self.client.on_disconnect = self._on_disconnect

    def _on_connect(self, client, userdata, flags, reason_code, properties):
        self.connected = reason_code == 0

    def _on_disconnect(self, client, userdata, flags, reason_code, properties):
        self.connected = False

    def connect(self, timeout=10.0):
        """Kết nối MQTT và chờ broker xác nhận."""
        self.client.connect(self.host, self.port, keepalive=30)
        self.client.loop_start()
        deadline = time.monotonic() + timeout

        while not self.connected and time.monotonic() < deadline:
            time.sleep(0.1)

        if not self.connected:
            self.close()
            raise ConnectionError("MQTT connection timeout")

    def publish_status(self, is_drowsy, confidence=1.0):
        """Gửi trạng thái AI; chỉ xác nhận thành công sau MQTT PUBACK."""
        if not self.connected:
            raise ConnectionError("MQTT is disconnected")

        confidence = float(confidence)
        if not 0.0 <= confidence <= 1.0:
            raise ValueError("Confidence must be between 0 and 1")

        payload = {
            "deviceId": self.device_id,
            "status": "ONLINE",
            "heartRate": None,
            "spo2": None,
            "edgeAiStatus": "DROWSY" if is_drowsy else "NORMAL",
            "edgeAiConfidence": confidence,
            "isDrowsy": bool(is_drowsy),
        }

        info = self.client.publish(
            self.topic,
            json.dumps(payload, ensure_ascii=False),
            qos=1,
            retain=False,
        )

        if info.rc != mqtt.MQTT_ERR_SUCCESS:
            raise RuntimeError(f"MQTT publish failed: {info.rc}")

        info.wait_for_publish(timeout=10)

        if not info.is_published():
            raise TimeoutError("MQTT PUBACK timeout")

        return payload

    def close(self):
        """Giải phóng kết nối MQTT."""
        self.client.disconnect()
        self.client.loop_stop()
        self.connected = False

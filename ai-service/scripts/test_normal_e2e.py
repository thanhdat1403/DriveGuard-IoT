
import json
import paho.mqtt.client as mqtt

# D? li?u mô ph?ng tài x? tr? l?i tr?ng thái b́nh thu?ng.
payload = {
    "deviceId": "ESP32S3-E2E-001",
    "status": "ONLINE",
    "heartRate": None,
    "spo2": None,
    "edgeAiStatus": "NORMAL",
    "edgeAiConfidence": 1.0,
    "isDrowsy": False
}

client = mqtt.Client(
    mqtt.CallbackAPIVersion.VERSION2,
    client_id="driveguard-d5c-normal"
)

try:
    client.connect("127.0.0.1", 1884, 60)
    client.loop_start()

    # QoS 1 xác nh?n broker dă nh?n b?n tin.
    result = client.publish(
        "driveguard/e2e/esp32s3/telemetry",
        json.dumps(payload),
        qos=1,
        retain=False
    )

    result.wait_for_publish(timeout=10)

    print("PUBLISH_RC =", result.rc)
    print("PUBLISH_CONFIRMED =", result.is_published())

    if result.rc != mqtt.MQTT_ERR_SUCCESS or not result.is_published():
        raise RuntimeError("MQTT publish was not confirmed")

finally:
    client.disconnect()
    client.loop_stop()

"""Kiểm thử gửi một bản tin Telemetry NORMAL qua MQTT E2E."""

import json
import sys
import time

import paho.mqtt.client as mqtt


BROKER_HOST = "127.0.0.1"
BROKER_PORT = 1884
TOPIC = "driveguard/e2e/esp32s3/telemetry"
DEVICE_ID = "ESP32S3-E2E-001"


def main() -> int:
    # Bản tin thử nghiệm an toàn: không tạo cảnh báo ngủ gật.
    payload = {
        "deviceId": DEVICE_ID,
        "status": "ONLINE",
        "heartRate": None,
        "spo2": None,
        "edgeAiStatus": "NORMAL",
        "edgeAiConfidence": 1.0,
        "isDrowsy": False,
    }

    client = mqtt.Client(
        callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
        client_id=f"driveguard-ai-smoke-{int(time.time())}",
        protocol=mqtt.MQTTv311,
    )

    connected = False

    def on_connect(client, userdata, flags, reason_code, properties):
        nonlocal connected
        connected = reason_code == 0
        print(f"MQTT_CONNECT_RESULT={reason_code}", flush=True)

    client.on_connect = on_connect

    try:
        client.connect(BROKER_HOST, BROKER_PORT, keepalive=30)
        client.loop_start()

        # Đợi kết nối được xác nhận trước khi publish.
        deadline = time.monotonic() + 10
        while not connected and time.monotonic() < deadline:
            time.sleep(0.1)

        if not connected:
            print("FAIL: MQTT connection not confirmed")
            return 1

        message = json.dumps(payload, ensure_ascii=False)
        info = client.publish(TOPIC, message, qos=1, retain=False)

        if info.rc != mqtt.MQTT_ERR_SUCCESS:
            print(f"FAIL: MQTT publish rc={info.rc}")
            return 1

        info.wait_for_publish(timeout=10)

        if not info.is_published():
            print("FAIL: MQTT publish not confirmed")
            return 1

        print(f"TOPIC={TOPIC}")
        print(f"DEVICE_ID={DEVICE_ID}")
        print(f"PAYLOAD={message}")
        print("PASS: MQTT QoS1 publish confirmed")
        return 0

    except Exception as exc:
        print(f"FAIL: {type(exc).__name__}: {exc}")
        return 1
    finally:
        client.disconnect()
        client.loop_stop()


if __name__ == "__main__":
    sys.exit(main())

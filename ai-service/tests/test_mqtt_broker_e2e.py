
"""Kiểm thử MQTT Publisher -> Broker -> Subscriber trên E2E."""

import json
import sys
import threading
import uuid
from pathlib import Path

import paho.mqtt.client as mqtt

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from mqtt_publisher import DriveGuardMqttPublisher


HOST = "127.0.0.1"
PORT = 1884
TOPIC = f"driveguard/e2e/tests/subscriber-{uuid.uuid4().hex[:8]}"


def main():
    received = []
    subscribed = threading.Event()
    message_received = threading.Event()

    subscriber = mqtt.Client(
        callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
        client_id=f"driveguard-test-sub-{uuid.uuid4().hex[:8]}",
    )

    def on_connect(client, userdata, flags, reason_code, properties):
        if reason_code == 0:
            client.subscribe(TOPIC, qos=1)

    def on_subscribe(client, userdata, mid, reason_codes, properties):
        if reason_codes and all(not rc.is_failure for rc in reason_codes):
            subscribed.set()

    def on_message(client, userdata, message):
        received.append(json.loads(message.payload.decode("utf-8")))
        message_received.set()

    subscriber.on_connect = on_connect
    subscriber.on_subscribe = on_subscribe
    subscriber.on_message = on_message

    publisher = DriveGuardMqttPublisher(
        host=HOST,
        port=PORT,
        topic=TOPIC,
        device_id="ESP32S3-E2E-TEST",
    )

    try:
        subscriber.connect(HOST, PORT, keepalive=30)
        subscriber.loop_start()

        if not subscribed.wait(timeout=5):
            raise AssertionError("SUBSCRIBER_READY=False")

        print("SUBSCRIBER_READY=True", flush=True)

        publisher.connect(timeout=5)
        print("PUBLISHER_CONNECTED=True", flush=True)

        publisher.publish_status(is_drowsy=True, confidence=1.0)

        if not message_received.wait(timeout=5):
            raise AssertionError("MESSAGE_RECEIVED=False")

        payload = received[0]

        assert payload["deviceId"] == "ESP32S3-E2E-TEST"
        assert payload["edgeAiStatus"] == "DROWSY"
        assert payload["isDrowsy"] is True
        assert payload["edgeAiConfidence"] == 1.0

        print("MESSAGE_RECEIVED=True", flush=True)
        print(f"RECEIVED_STATUS={payload['edgeAiStatus']}", flush=True)
        print(f"RECEIVED_DEVICE_ID={payload['deviceId']}", flush=True)
        print("G03_08_45_21_D3_RESULT=PASS", flush=True)

    finally:
        publisher.close()
        subscriber.disconnect()
        subscriber.loop_stop()


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"G03_08_45_21_D3_RESULT=FAIL:{exc}", flush=True)
        sys.exit(1)

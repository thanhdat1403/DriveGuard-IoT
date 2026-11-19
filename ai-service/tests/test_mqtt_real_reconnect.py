
"""Kiểm thử reconnect với Mosquitto Docker cô lập tại cổng 1885."""

import json
import subprocess
import sys
import threading
import time
import uuid
from pathlib import Path

import paho.mqtt.client as mqtt

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from mqtt_event_worker import MqttEventWorker
from mqtt_publisher import DriveGuardMqttPublisher


HOST = "127.0.0.1"
PORT = 1885
CONTAINER = "driveguard-mqtt-reconnect-test"
TOPIC = f"driveguard/e2e/tests/reconnect-{uuid.uuid4().hex[:8]}"


def docker_action(action):
    # Chỉ thao tác với container thử nghiệm.
    result = subprocess.run(
        ["docker", action, CONTAINER],
        capture_output=True,
        text=True,
        timeout=30,
    )
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip())


def wait_for_broker(timeout=15):
    import socket

    deadline = time.monotonic() + timeout

    while time.monotonic() < deadline:
        try:
            with socket.create_connection((HOST, PORT), timeout=1):
                return True
        except OSError:
            time.sleep(0.2)

    return False


def main():
    worker = MqttEventWorker(max_attempts=3, retry_delay=0.5)
    worker.publisher = DriveGuardMqttPublisher(
        host=HOST,
        port=PORT,
        topic=TOPIC,
        device_id="ESP32S3-RECONNECT-TEST",
    )

    received = []
    received_event = threading.Event()

    subscriber = mqtt.Client(
        callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
        client_id=f"driveguard-reconnect-sub-{uuid.uuid4().hex[:8]}",
    )

    def on_message(client, userdata, message):
        payload = json.loads(message.payload.decode("utf-8"))
        received.append(payload)
        received_event.set()

    subscriber.on_message = on_message

    try:
        if not wait_for_broker():
            raise RuntimeError("BROKER_NOT_READY")

        worker.start()
        time.sleep(1)

        # Dừng riêng broker kiểm thử.
        docker_action("stop")
        print("TEST_BROKER_STOPPED=True", flush=True)

        worker.submit(True)
        print("DROWSY_SUBMITTED=True", flush=True)

        time.sleep(1)

        docker_action("start")
        print("TEST_BROKER_RESTARTED=True", flush=True)

        if not wait_for_broker():
            raise RuntimeError("BROKER_RESTART_FAILED")

        subscriber.connect(HOST, PORT, keepalive=30)
        subscriber.subscribe(TOPIC, qos=1)
        subscriber.loop_start()

        received_event.wait(timeout=8)

        print(f"RECEIVED_COUNT={len(received)}", flush=True)
        print(f"FAILED_EVENTS={worker.failed_events}", flush=True)

        passed = (
            len(received) >= 1
            and any(p.get("edgeAiStatus") == "DROWSY" for p in received)
            and worker.failed_events == 0
        )

        print(
            f"G03_08_45_21_D4B_RESULT={'PASS' if passed else 'FAIL'}",
            flush=True,
        )

        if not passed:
            sys.exit(1)

    finally:
        worker.close(timeout=15)
        subscriber.disconnect()
        subscriber.loop_stop()

        # Khôi phục broker thử nghiệm nếu bài test gặp lỗi.
        subprocess.run(
            ["docker", "start", CONTAINER],
            capture_output=True,
            timeout=30,
        )


if __name__ == "__main__":
    main()


"""Kiểm thử Worker tự kết nối lại khi Publisher mất kết nối."""

import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from mqtt_event_worker import MqttEventWorker


class FakeReconnectPublisher:
    def __init__(self):
        self.connected = False
        self.connect_attempts = 0
        self.publish_attempts = 0
        self.delivered_events = []
        self.closed = False

    def connect(self, timeout=3.0):
        self.connect_attempts += 1

        # Lần đầu kết nối thất bại, lần tiếp theo thành công.
        if self.connect_attempts == 1:
            self.connected = False
            raise ConnectionError("SIMULATED_CONNECTION_FAILURE")

        self.connected = True

    def publish_status(self, is_drowsy, confidence=1.0):
        self.publish_attempts += 1

        if not self.connected:
            raise ConnectionError("MQTT is disconnected")

        status = "DROWSY" if is_drowsy else "NORMAL"
        self.delivered_events.append(status)

        return {"edgeAiStatus": status}

    def close(self):
        self.closed = True
        self.connected = False


def main():
    worker = MqttEventWorker(max_attempts=3, retry_delay=0.1)
    fake = FakeReconnectPublisher()
    worker.publisher = fake

    worker.start()

    accepted = worker.submit(True)
    print(f"EVENT_ACCEPTED={accepted}", flush=True)

    # Chờ tối đa 5 giây để worker xử lý sự kiện.
    deadline = time.monotonic() + 5.0

    while time.monotonic() < deadline:
        if fake.delivered_events:
            break
        time.sleep(0.05)

    stopped = worker.close(timeout=5.0)

    print(f"CONNECT_ATTEMPTS={fake.connect_attempts}", flush=True)
    print(f"PUBLISH_ATTEMPTS={fake.publish_attempts}", flush=True)
    print(f"DELIVERED_EVENTS={fake.delivered_events}", flush=True)
    print(f"FAILED_EVENTS={worker.failed_events}", flush=True)
    print(f"WORKER_STOPPED={stopped}", flush=True)
    print(f"PUBLISHER_CLOSED={fake.closed}", flush=True)

    passed = (
        accepted
        and fake.connect_attempts >= 2
        and fake.delivered_events == ["DROWSY"]
        and worker.failed_events == 0
        and stopped
        and fake.closed
    )

    print(
        f"G03_08_45_21_D4A_RESULT={'PASS' if passed else 'FAIL'}",
        flush=True,
    )

    if not passed:
        sys.exit(1)


if __name__ == "__main__":
    main()

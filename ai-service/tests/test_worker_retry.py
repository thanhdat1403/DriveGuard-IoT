"""Kiem thu retry khong ket noi broker, camera hay database."""
import sys
import pathlib

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from mqtt_event_worker import MqttEventWorker


class FakePublisher:
    def __init__(self):
        self.connected = False
        self.attempts = 0
        self.sent = []
        self.closed = False

    def connect(self, timeout=3.0):
        self.connected = True

    def publish_status(self, is_drowsy, confidence=1.0):
        self.attempts += 1
        if self.attempts == 1:
            raise ConnectionError("SIMULATED_MQTT_FAILURE")
        status = "DROWSY" if is_drowsy else "NORMAL"
        self.sent.append(status)
        return {"edgeAiStatus": status}

    def close(self):
        self.closed = True


worker = MqttEventWorker(max_attempts=3, retry_delay=0.01)
fake = FakePublisher()
worker.publisher = fake
worker.start()
accepted = worker.submit(True)
worker.events.join()
stopped = worker.close()
assert accepted
assert fake.attempts == 2, fake.attempts
assert fake.sent == ["DROWSY"], fake.sent
assert stopped and fake.closed
assert worker.failed_events == 0
print("G03_08_45_21C_RETRY_TEST=PASS")
print(f"PUBLISH_ATTEMPTS={fake.attempts}")
print(f"DELIVERED_EVENTS={fake.sent}")
print(f"WORKER_STOPPED={stopped}")

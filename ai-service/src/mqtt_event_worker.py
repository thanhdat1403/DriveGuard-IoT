"""Xu ly su kien AI tren luong MQTT rieng, co retry gioi han."""
import queue
import threading
import time
from mqtt_publisher import DriveGuardMqttPublisher


class MqttEventWorker:
    def __init__(self, max_attempts=3, retry_delay=0.5):
        self.publisher = DriveGuardMqttPublisher()
        self.events = queue.Queue(maxsize=32)
        self.stop_event = threading.Event()
        self.thread = None
        self.max_attempts = max_attempts
        self.retry_delay = retry_delay
        self.failed_events = 0

    def start(self):
        if self.thread is not None:
            return
        self.thread = threading.Thread(target=self._run, daemon=True)
        self.thread.start()

    def submit(self, is_drowsy):
        if self.stop_event.is_set():
            return False
        try:
            self.events.put_nowait(bool(is_drowsy))
            return True
        except queue.Full:
            print("AI_MQTT_QUEUE_FULL=True", flush=True)
            return False

    def _run(self):
        try:
            try:
                self.publisher.connect(timeout=3.0)
                print("AI_MQTT_CONNECTED=True", flush=True)
            except Exception as exc:
                print(f"AI_MQTT_CONNECT_ERROR={exc}", flush=True)

            while not self.stop_event.is_set() or not self.events.empty():
                try:
                    status = self.events.get(timeout=0.2)
                except queue.Empty:
                    continue

                try:
                    sent = False
                    for attempt in range(1, self.max_attempts + 1):
                        try:
                            # Ket noi lai khi publisher bao mat ket noi.
                            if not self.publisher.connected:
                                self.publisher.connect(timeout=3.0)
                            payload = self.publisher.publish_status(
                                is_drowsy=status, confidence=1.0
                            )
                            print(f"AI_MQTT_SENT={payload['edgeAiStatus']}", flush=True)
                            sent = True
                            break
                        except Exception as exc:
                            print(f"AI_MQTT_SEND_ERROR_ATTEMPT_{attempt}={exc}", flush=True)
                            if attempt < self.max_attempts:
                                time.sleep(self.retry_delay * attempt)
                    if not sent:
                        self.failed_events += 1
                        print(f"AI_MQTT_EVENT_FAILED={'DROWSY' if status else 'NORMAL'}", flush=True)
                finally:
                    self.events.task_done()
        finally:
            self.publisher.close()

    def close(self, timeout=15.0):
        self.stop_event.set()
        if self.thread is not None:
            self.thread.join(timeout=timeout)
            if self.thread.is_alive():
                print("AI_MQTT_SHUTDOWN_TIMEOUT=True", flush=True)
                return False
        return True

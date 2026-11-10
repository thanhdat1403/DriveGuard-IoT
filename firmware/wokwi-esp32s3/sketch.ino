#include <Arduino.h>

#include <WiFi.h>

#include <PubSubClient.h>

#include <Wire.h>

#include <SPI.h>

#include <SD.h>

#include <Adafruit_GFX.h>

#include <Adafruit_SSD1306.h>

#include <Adafruit_MPU6050.h>

#include <Adafruit_Sensor.h>

#include <math.h>

// DriveGuard IoT - Wokwi prototype. Camera/PPG values are simulated.

// NOT a medical diagnostic device or a validated driver-safety system.

constexpr int PIN_SDA = 8, PIN_SCL = 9;

constexpr int PIN_LED_GREEN = 4, PIN_LED_YELLOW = 5;

constexpr int PIN_LED_RED = 6, PIN_LED_VIB = 7;

constexpr int PIN_SD_CS = 10, PIN_SD_MOSI = 11;

constexpr int PIN_SD_SCK = 12, PIN_SD_MISO = 13;

constexpr int PIN_BUZZER = 14, PIN_OK = 15, PIN_SOS = 16;

constexpr int PIN_CONTACT = 17, PIN_EYES = 18, PIN_HR_POT = 1;

constexpr unsigned long PUBLISH_INTERVAL = 5000;

constexpr unsigned long DISPLAY_INTERVAL = 300;

constexpr unsigned long ALERT_GRACE = 10000;

constexpr unsigned long BUTTON_DEBOUNCE = 40;

constexpr unsigned long DROWSY_THRESHOLD = 3000;

constexpr unsigned long OK_HOLD_DURATION = 3000;
constexpr unsigned long OK_DOUBLE_CLICK_WINDOW = 400;

constexpr unsigned long RECONNECT_INTERVAL = 5000;

const char *WIFI_SSID = "Wokwi-GUEST";

const char *WIFI_PASSWORD = "";

const char *MQTT_SERVER = "broker.hivemq.com";

constexpr uint16_t MQTT_PORT = 1883;

// MQTT Telemetry: du lieu cam bien dinh ky.

const char *MQTT_TOPIC =

    "driveguard/demo/esp32s3/telemetry";

// MQTT Events: su kien canh bao va thao tac nguoi lai.

const char *MQTT_EVENTS_TOPIC =

    "driveguard/demo/esp32s3/events";

// Ma thiet bi phai khop voi bang devices trong MariaDB.

const char *DEVICE_ID = "esp32s3-demo";

WiFiClient wifiClient;

PubSubClient mqttClient(wifiClient);

Adafruit_SSD1306 oled(128, 64, &Wire, -1);

Adafruit_MPU6050 mpu;

bool oledReady = false, imuReady = false, sdReady = false;

unsigned long lastPublish = 0, lastDisplay = 0;

unsigned long lastWifiAttempt = 0, lastMqttAttempt = 0;

unsigned long messageCount = 0;

// MQTT Events - TEST-V6 preparation.

// Dinh danh moi lan khoi dong firmware.

String bootId = "";

// So thu tu su kien trong mot lan khoi dong.

uint32_t eventSequence = 0;
// Identifies a single SOS episode, not an individual event.
uint32_t sosEpisodeSequence = 0;
String currentSosEpisodeId = "";

// So luong su kien toi da trong hang doi RAM.

constexpr size_t EVENT_QUEUE_SIZE = 10;

// Moi phan tu luu JSON cua mot su kien da duoc tao.

struct PendingEvent {

  String eventId;

  String payload;

  bool occupied = false;

};

// Hang doi su kien theo thu tu FIFO.

PendingEvent eventQueue[EVENT_QUEUE_SIZE];

size_t eventQueueHead = 0;

size_t eventQueueTail = 0;

size_t eventQueueCount = 0;

unsigned long eyesClosedSince = 0, alertSince = 0;

bool alertAcknowledged = false;

bool sosLatched = false;

bool sosSoundMuted = false;

bool lastEyesClosed = false, lastPpgContact = false, lastDrowsy = false;

float accelerationMagnitude = 0.0f;

struct Button {

  int pin = -1;

  bool lastRaw = HIGH;

  bool stable = HIGH;

  bool pressedEvent = false;

  bool releasedEvent = false;

  unsigned long changedAt = 0;

  void begin(int p) {

    pin = p;

    pinMode(pin, INPUT_PULLUP);

    lastRaw = digitalRead(pin);

    stable = lastRaw;

    changedAt = millis();

  }

  void update() {

    pressedEvent = false;

    releasedEvent = false;

    bool raw = digitalRead(pin);

    unsigned long now = millis();

    if (raw != lastRaw) {

      lastRaw = raw;

      changedAt = now;

    }

    if (now - changedAt >= BUTTON_DEBOUNCE && raw != stable) {

      stable = raw;

      if (stable == LOW) pressedEvent = true;

      else releasedEvent = true;

    }

  }

};

Button okButton, sosButton;

unsigned long okHoldStartedAt = 0;

bool okHoldHandled = false;

bool okPressStartedWithEyesOpen = false;
bool okSingleClickPending = false;
unsigned long okFirstReleaseAt = 0;

bool eyesClosed() { return digitalRead(PIN_EYES) == HIGH; }

// Tao Event ID duy nhat trong moi phien khoi dong.

// Chi goi khi mot su kien moi thuc su duoc tao.

String generateEventId() {

  eventSequence++;

  return String(DEVICE_ID) + "-" +

         bootId + "-" +

         String(eventSequence);

}

// Tao JSON su kien phu hop voi DeviceEventMessage cua Backend.

// timestampMs la uptime, khong phai thoi gian UTC.

String createEventPayload(const String &eventId,
                          const char *eventType,
                          unsigned long timestampMs) {
  String payload = "{";
  payload += "\"eventId\":\"" + eventId + "\",";
  payload += "\"deviceId\":\"" + String(DEVICE_ID) + "\",";
  payload += "\"eventType\":\"" + String(eventType) + "\",";
  if (currentSosEpisodeId.length() > 0) {
    payload += "\"sosEpisodeId\":\"" + currentSosEpisodeId + "\",";
  }
  payload += "\"timestampMs\":" + String(timestampMs) + ",";
  payload += "\"bootId\":\"" + bootId + "\",";
  payload += "\"sequence\":" + String(eventSequence);
  payload += "}";
  return payload;
}

// Them mot su kien moi vao hang doi RAM.

// Chua gui MQTT trong buoc nay.

bool enqueueEvent(const char *eventType) {

  if (eventQueueCount >= EVENT_QUEUE_SIZE) {

    Serial.println("[EVENT-QUEUE] FULL - event not queued");

    return false;

  }

  String eventId = generateEventId();

  String payload = createEventPayload(

      eventId,

      eventType,

      millis()

  );

  PendingEvent &item = eventQueue[eventQueueTail];

  item.eventId = eventId;

  item.payload = payload;

  item.occupied = true;

  eventQueueTail = (eventQueueTail + 1) % EVENT_QUEUE_SIZE;

  eventQueueCount++;

  Serial.print("[EVENT-QUEUE] Added | count=");

  Serial.println(eventQueueCount);

  Serial.print("[EVENT-QUEUE] Payload: ");

  Serial.println(payload);

  return true;

}

// Thu gui su kien dau hang doi qua MQTT.

// Ban demo QoS 0: publish thanh cong khong dong nghia Backend da luu.

void publishPendingEvents() {

  if (!mqttClient.connected() || eventQueueCount == 0) {

    return;

  }

  PendingEvent &item = eventQueue[eventQueueHead];

  if (!item.occupied) {

    Serial.println("[EVENT-QUEUE] Invalid queue state");

    return;

  }

  bool sent = mqttClient.publish(

      MQTT_EVENTS_TOPIC,

      item.payload.c_str()

  );

  if (!sent) {

    Serial.println("[MQTT-EVENT] Publish failed; event retained");

    return;

  }

  Serial.print("[MQTT-EVENT] Published QoS0 | eventId=");

  Serial.println(item.eventId);

  // Chi xoa khoi RAM sau khi publish duoc MQTT client chap nhan.

  // QoS0 KHONG dam bao broker/backend da nhan duoc.

  item.eventId = "";

  item.payload = "";

  item.occupied = false;

  eventQueueHead = (eventQueueHead + 1) % EVENT_QUEUE_SIZE;

  eventQueueCount--;

}

bool ppgContact() { return digitalRead(PIN_CONTACT) == HIGH; }

int simulatedHeartRate() { return map(analogRead(PIN_HR_POT), 0, 4095, 50, 140); }

void logSD(const String &event) {

  String line = String(millis()) + "," + event;

  if (!sdReady) {

    Serial.print("[SD] SKIPPED: "); Serial.println(line);

    return;

  }

  File f = SD.open("/events.csv", FILE_APPEND);

  if (!f) {

    Serial.print("[SD] WRITE FAILED: "); Serial.println(line);

    return;

  }

  size_t written = f.println(line);

  f.close();

  if (written) {

    Serial.print("[SD] LOGGED: "); Serial.println(line);

  } else {

    Serial.print("[SD] WRITE FAILED: "); Serial.println(line);

  }

}

void printSDLog() {

  if (!sdReady) { Serial.println("[SD] Not ready"); return; }

  File f = SD.open("/events.csv", FILE_READ);

  if (!f) { Serial.println("[SD] Cannot open events.csv"); return; }

  Serial.println("=== EVENTS.CSV ===");

  while (f.available()) Serial.write(f.read());

  f.close();

  Serial.println("\n=== END EVENTS.CSV ===");

}

void connectWiFi() {
  static bool started = false;
  static unsigned long startedAt = 0;
  static unsigned long lastLogAt = 0;
  const unsigned long now = millis();

  if (WiFi.status() == WL_CONNECTED) {
    if (started) {
      Serial.print("[WIFI] Connected | IP=");
      Serial.println(WiFi.localIP());
      started = false;
    }
    return;
  }

  if (!started || now - startedAt >= 30000UL) {
    if (started) {
      Serial.print("[WIFI] Retry after timeout | status=");
      Serial.println((int)WiFi.status());
      WiFi.disconnect();
    }
    WiFi.mode(WIFI_STA);
    // Do not pin channel: let Wokwi scan for Wokwi-GUEST.
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    started = true;
    startedAt = now;
    lastLogAt = now;
    Serial.println("[WIFI] Connecting to Wokwi-GUEST (auto channel)...");
  } else if (now - lastLogAt >= 5000UL) {
    lastLogAt = now;
    Serial.print("[WIFI] Waiting | status=");
    Serial.println((int)WiFi.status());
  }
}

void connectMQTT() {

  if (WiFi.status() != WL_CONNECTED || mqttClient.connected()) return;

  unsigned long now = millis();

  if (now - lastMqttAttempt < RECONNECT_INTERVAL) return;

  lastMqttAttempt = now;

  String id = "driveguard-wokwi-" + String((uint32_t)ESP.getEfuseMac(), HEX);

  if (mqttClient.connect(id.c_str())) {

    Serial.println("[MQTT] Connected");

  } else {

    Serial.print("[MQTT] Error state="); Serial.println(mqttClient.state());

  }

}

void activateSOS(const char *serialMessage, const char *sdEvent) {
  if (sosLatched) {
    Serial.println("[SOS] Already ACTIVE; ignoring repeated activation");
    return;
  }
  sosEpisodeSequence++;
  currentSosEpisodeId = String(DEVICE_ID) + "-" + bootId + "-sos-" + String(sosEpisodeSequence);
  sosLatched = true;
  sosSoundMuted = false;
  alertAcknowledged = false;
  Serial.print("[SOS] Episode started: ");
  Serial.println(currentSosEpisodeId);
  Serial.println(serialMessage);
  logSD(sdEvent);
  if (!enqueueEvent(sdEvent)) {
    Serial.println("[SOS] WARNING: event queue FULL; start event not queued");
  }
}

// Demo-only: explicit operator action to end an SOS episode.
// Acknowledging or muting never ends it.
void endCurrentSOSAfterQueued() {
  if (!sosLatched || currentSosEpisodeId.isEmpty()) return;
  logSD("SOS_ENDED");
  Serial.print("[SOS] Episode ended by long GREEN OK press: ");
  Serial.println(currentSosEpisodeId);
  sosLatched = false;
  sosSoundMuted = false;
  alertAcknowledged = true;
  alertSince = 0;
  currentSosEpisodeId = "";
  noTone(PIN_BUZZER);
}

void updateState() {

  okButton.update();

  sosButton.update();

  const unsigned long now = millis();

  const bool closed = eyesClosed();

  const bool contact = ppgContact();

  if (closed != lastEyesClosed) {

    Serial.println(closed ? "[EYES SIM] CLOSED" : "[EYES SIM] OPEN");

    logSD(closed ? "EYES_CLOSED" : "EYES_OPEN");

    lastEyesClosed = closed;

    if (closed) {

      eyesClosedSince = now;

      alertSince = 0;

      alertAcknowledged = false;

    } else {

      eyesClosedSince = 0;

      alertSince = 0;

      // Keep acknowledgement and SOS state separate from eye state.

    }

  }

  if (contact != lastPpgContact) {

    Serial.println(contact ? "[PPG SIM] CONTACT ON" : "[PPG SIM] CONTACT OFF");

    lastPpgContact = contact;

  }

  // RED SOS: activate immediately on a debounced press. Never ends an SOS.
  if (sosButton.pressedEvent) {
    if (!sosLatched) {
      activateSOS("[SOS] Manual SOS activated (SIMULATION)", "MANUAL_SOS");
    } else {
      Serial.println("[SOS] Red button ignored: episode already ACTIVE");
    }
  }

  // GREEN OK: short click = ACK, double click = mute, hold >=3s = END.
  // A short click is delayed 400ms to distinguish it from a double click.
  if (okButton.pressedEvent) {
    okHoldStartedAt = now;
    okHoldHandled = false;
    okPressStartedWithEyesOpen = !closed;
  }

  if (okButton.stable == LOW && okPressStartedWithEyesOpen &&
      !closed && sosLatched && !okHoldHandled &&
      now - okHoldStartedAt >= OK_HOLD_DURATION) {
    // Long press wins over any pending short click.
    okSingleClickPending = false;
    if (enqueueEvent("SOS_ENDED")) {
      endCurrentSOSAfterQueued();
    } else {
      Serial.println("[SOS] END rejected: MQTT event queue FULL");
    }
    okHoldHandled = true;
  }

  if (okButton.releasedEvent) {
    if (okHoldHandled) {
      // No short/double click after a long press.
    } else if (!okPressStartedWithEyesOpen || closed) {
      Serial.println("[OK] Ignored: eyes closed during button press");
      logSD("ACK_IGNORED_EYES_CLOSED");
      okSingleClickPending = false;
    } else if (okSingleClickPending &&
               now - okFirstReleaseAt <= OK_DOUBLE_CLICK_WINDOW) {
      okSingleClickPending = false;
      if (sosLatched && !sosSoundMuted) {
        if (enqueueEvent("SOS_SOUND_MUTED")) {
          sosSoundMuted = true;
          noTone(PIN_BUZZER);
          logSD("SOS_SOUND_MUTED");
          Serial.println("[SOS] Double GREEN OK: sound muted, SOS remains ACTIVE");
        } else {
          Serial.println("[SOS] Mute event not queued: queue FULL");
        }
      } else {
        Serial.println("[OK] Double click: no active unmuted SOS");
      }
    } else {
      okSingleClickPending = true;
      okFirstReleaseAt = now;
    }
    okHoldStartedAt = 0;
    okHoldHandled = false;
    okPressStartedWithEyesOpen = false;
  }

  // Dispatch a single click only after the double-click window expires.
  if (okSingleClickPending &&
      now - okFirstReleaseAt > OK_DOUBLE_CLICK_WINDOW) {
    okSingleClickPending = false;
    if (closed) {
      Serial.println("[OK] ACK ignored: eyes currently closed");
    } else if (sosLatched) {
      if (!alertAcknowledged) {
        if (enqueueEvent("SOS_ACKNOWLEDGED")) {
          alertAcknowledged = true;
          logSD("SOS_ACKNOWLEDGED");
          Serial.println("[OK] Single GREEN OK: SOS acknowledged, still ACTIVE");
        } else {
          Serial.println("[OK] ACK not queued: queue FULL");
        }
      } else {
        Serial.println("[OK] SOS already acknowledged");
      }
    } else {
      alertAcknowledged = true;
      logSD("DROWSINESS_ACKNOWLEDGED");
      Serial.println("[OK] Drowsiness warning acknowledged");
    }
  }

  const bool drowsy = closed && (now - eyesClosedSince >= DROWSY_THRESHOLD);

  if (drowsy && !lastDrowsy) {

    Serial.println("[AI SIM] DROWSY: eyes closed for 3s");

    logSD("DROWSY");

    if (!alertAcknowledged) alertSince = now;

  }

  if (!drowsy) alertSince = 0;

  lastDrowsy = drowsy;

  if (drowsy && !alertAcknowledged && alertSince != 0 &&

      now - alertSince >= ALERT_GRACE && !sosLatched) {

    activateSOS("[SOS SIM] No response to drowsiness warning", "NO_RESPONSE_SOS");

  }

  const bool warning = drowsy && !alertAcknowledged;

  const bool soundWarning = warning && !sosLatched;

  const bool soundSOS = sosLatched && !sosSoundMuted;

  digitalWrite(PIN_LED_GREEN, !warning && !sosLatched ? HIGH : LOW);

  digitalWrite(PIN_LED_YELLOW, warning && !sosLatched ? HIGH : LOW);

  digitalWrite(PIN_LED_RED, sosLatched ? HIGH : LOW);

  digitalWrite(PIN_LED_VIB, (soundWarning || soundSOS) ? HIGH : LOW);

  if (soundWarning || soundSOS) {

    bool pulse = (now / 350) % 2 == 0;

    if (pulse) tone(PIN_BUZZER, soundSOS ? 1800 : 1100);

    else noTone(PIN_BUZZER);

  } else {

    noTone(PIN_BUZZER);

  }

}

void updateIMU() {

  if (!imuReady) return;

  sensors_event_t a, g, t;

  mpu.getEvent(&a, &g, &t);

  accelerationMagnitude = sqrtf(

      a.acceleration.x * a.acceleration.x +

      a.acceleration.y * a.acceleration.y +

      a.acceleration.z * a.acceleration.z);

}

void updateDisplay() {

  if (!oledReady || millis() - lastDisplay < DISPLAY_INTERVAL) return;

  lastDisplay = millis();

  bool drowsy = eyesClosed() && eyesClosedSince != 0 &&

                millis() - eyesClosedSince >= DROWSY_THRESHOLD;

  oled.clearDisplay();

  oled.setTextColor(SSD1306_WHITE);

  oled.setTextSize(1);

  oled.setCursor(0, 0);

  oled.println("DriveGuard IoT | Wokwi");

  oled.print("WiFi:"); oled.println(WiFi.status() == WL_CONNECTED ? "OK" : "OFF");

  oled.print("MQTT:"); oled.println(mqttClient.connected() ? "OK" : "OFF");

  oled.print("AI SIM:"); oled.println(drowsy ? "DROWSY" : "NORMAL");

  oled.print("PPG SIM:");

  if (ppgContact()) { oled.print(simulatedHeartRate()); oled.println(" BPM"); }

  else oled.println("NO CONTACT");

  oled.print("IMU:");

  if (imuReady) oled.println(accelerationMagnitude, 1);

  else oled.println("OFF");

  oled.print("SOS:");

  if (!sosLatched) oled.println("NO");

  else oled.println(sosSoundMuted ? "MUTED" : "ACTIVE");

  oled.display();

}

String createSensorPayload() {

  // Keep JSON fields compatible with existing Spring Boot subscriber.

  // 0 for HR/SpO2 without contact is a legacy sentinel, NOT a measurement.

  bool contact = ppgContact();

  bool drowsy = eyesClosed() && eyesClosedSince != 0 &&

                millis() - eyesClosedSince >= DROWSY_THRESHOLD;

  String payload = "{";

  payload += "\"deviceId\":\"" + String(DEVICE_ID) + "\",";

  payload += "\"status\":\"ONLINE\",";

  payload += "\"messageCount\":" + String(messageCount) + ",";

  payload += "\"heartRate\":" + String(contact ? simulatedHeartRate() : 0) + ",";

  payload += "\"spo2\":" + String(contact ? 98 : 0) + ",";

  payload += "\"edgeAiStatus\":\"" + String(drowsy ? "DROWSY" : "NORMAL") + "\",";

  payload += "\"edgeAiConfidence\":0.95,";

  payload += "\"isDrowsy\":" + String(drowsy ? "true" : "false");

  payload += "}";

  return payload;

}

void publishTelemetry() {

  if (!mqttClient.connected() || millis() - lastPublish < PUBLISH_INTERVAL) return;

  lastPublish = millis();

  messageCount++;

  String payload = createSensorPayload();

  if (mqttClient.publish(MQTT_TOPIC, payload.c_str())) {

    Serial.print("[MQTT] Publish: "); Serial.println(payload);

    if (!ppgContact()) {

      Serial.println("[PPG SIM] No contact: 0 is a temporary compatibility sentinel, NOT a measurement");

    }

  } else {

    Serial.println("[MQTT] Publish failed");

  }

}

void setup() {

  Serial.begin(115200);

  delay(300);

  // Tao Boot ID moi cho moi lan khoi dong.

uint64_t bootRandom =

    ((uint64_t)esp_random() << 32) | esp_random();

char bootIdBuffer[17];

snprintf(

    bootIdBuffer,

    sizeof(bootIdBuffer),

    "%016llx",

    (unsigned long long)bootRandom

);

bootId = String(bootIdBuffer);

eventSequence = 0;

Serial.print("[EVENT] Boot ID: ");

Serial.println(bootId);

  Serial.println("\n=== DriveGuard IoT - Wokwi TEST-V8 SOS BUTTON LOGIC ===");

  pinMode(PIN_LED_GREEN, OUTPUT);

  pinMode(PIN_LED_YELLOW, OUTPUT);

  pinMode(PIN_LED_RED, OUTPUT);

  pinMode(PIN_LED_VIB, OUTPUT);

  pinMode(PIN_BUZZER, OUTPUT);

  pinMode(PIN_EYES, INPUT);

  pinMode(PIN_CONTACT, INPUT);

  okButton.begin(PIN_OK);

  sosButton.begin(PIN_SOS);

  analogReadResolution(12);

  // Preserve SD-first initialization from the working TEST-V4 firmware.

  delay(1000);

  pinMode(PIN_SD_CS, OUTPUT);

  digitalWrite(PIN_SD_CS, HIGH);

  SPI.begin(PIN_SD_SCK, PIN_SD_MISO, PIN_SD_MOSI, PIN_SD_CS);

  Serial.println("[SD] Initializing...");

  sdReady = SD.begin(PIN_SD_CS, SPI, 1000000);

  Serial.println(sdReady ? "[SD] INIT OK" : "[SD] INIT FAILED");

  if (sdReady) {

    if (!SD.exists("/events.csv")) {

      File f = SD.open("/events.csv", FILE_WRITE);

      if (f) {

        size_t n = f.println("timestamp_ms,event");

        f.close();

        Serial.println(n > 0 ? "[SD] events.csv CREATED" : "[SD] HEADER WRITE FAILED");

      } else {

        Serial.println("[SD] Cannot create events.csv");

      }

    }

    logSD("SYSTEM_STARTED");

    File verify = SD.open("/events.csv", FILE_READ);

    if (verify) {

      Serial.print("[SD] events.csv READ OK, bytes=");

      Serial.println(verify.size());

      verify.close();

    } else {

      Serial.println("[SD] events.csv READ FAILED");

    }

  }

  Wire.begin(PIN_SDA, PIN_SCL);

  oledReady = oled.begin(SSD1306_SWITCHCAPVCC, 0x3C);

  Serial.println(oledReady ? "[OLED] OK 0x3C" : "[OLED] NOT FOUND");

  imuReady = mpu.begin(0x68, &Wire);

  Serial.println(imuReady ? "[MPU6050] OK 0x68" : "[MPU6050] NOT FOUND");

  mqttClient.setServer(MQTT_SERVER, MQTT_PORT);

  mqttClient.setBufferSize(512);

  lastWifiAttempt = millis() - RECONNECT_INTERVAL;

  lastMqttAttempt = millis() - RECONNECT_INTERVAL;

  connectWiFi();

}

void loop() {

  while (Serial.available()) {

    char command = Serial.read();

    if (command == 'L' || command == 'l') printSDLog();

  }

  connectWiFi();

  connectMQTT();

  if (mqttClient.connected()) mqttClient.loop();

  updateState();

  updateIMU();

  updateDisplay();

  publishTelemetry();

  publishPendingEvents();

  delay(10);

}

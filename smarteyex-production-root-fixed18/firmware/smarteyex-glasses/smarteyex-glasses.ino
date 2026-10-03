// SmartEyeX glasses firmware — BLE link for ESP32-C3 (protocol v1, see ../README.md)
//
// STATUS: written against NimBLE-Arduino 1.4.x. NOT compiled or tested on hardware in the
// environment where it was authored. Treat it as a starting point and test on your board.
//
// What works by design: BLE service, bonded/encrypted command channel, status (battery/LED),
// ping, LED control, chunked JPEG transfer with CRC32.
// What is NOT included: a camera driver. The ESP32-C3 has no DVP camera interface, so a camera
// needs an SPI/UART JPEG module (or an ESP32-S3 + esp_camera). Implement cameraCaptureJpeg()
// and set CAMERA_ENABLED to 1. Until then the glasses honestly report "camera not ready".
// Microphone/audio streaming is also not part of protocol v1; MIC_ENABLED stays 0.

#include <NimBLEDevice.h>

#define PROTOCOL_VERSION 1

// ---- board configuration (edit for your wiring) ----
#define LED_PIN 3              // external capture LED, active high
#define BATTERY_ADC_PIN 4      // battery through a 1:2 divider (e.g. 100k/100k)
#define CAMERA_ENABLED 0       // set to 1 after implementing cameraCaptureJpeg()
#define MIC_ENABLED 0          // audio streaming not implemented in protocol v1

static const char* SERVICE_UUID = "7e5e0001-5a1e-4e58-9c1d-0a5e7e5e0001";
static const char* COMMAND_UUID = "7e5e0002-5a1e-4e58-9c1d-0a5e7e5e0001";
static const char* EVENT_UUID   = "7e5e0003-5a1e-4e58-9c1d-0a5e7e5e0001";
static const char* FRAME_UUID   = "7e5e0004-5a1e-4e58-9c1d-0a5e7e5e0001";

enum : uint8_t { OP_PING = 0x01, OP_GET_STATUS = 0x02, OP_CAPTURE = 0x03, OP_SET_LED = 0x04 };
enum : uint8_t { EV_PONG = 0x81, EV_STATUS = 0x82, EV_FRAME_BEGIN = 0x83, EV_FRAME_END = 0x84, EV_ERROR = 0x85 };
enum : uint8_t { ERR_CAMERA_NOT_READY = 1, ERR_CAPTURE_FAILED = 2, ERR_BUSY = 3, ERR_UNKNOWN_COMMAND = 4 };

static NimBLECharacteristic* eventChar = nullptr;
static NimBLECharacteristic* frameChar = nullptr;
static volatile uint16_t peerMtu = 23;
static volatile uint8_t pendingOp = 0;   // written by BLE callback, handled in loop()
static volatile uint8_t pendingArg = 0;
static bool ledOn = false;
static uint8_t frameCounter = 0;

// ---- camera hook: implement for your hardware ----
// Return true and point *data/*len at a complete JPEG that stays valid until the next call.
static bool cameraCaptureJpeg(const uint8_t** data, size_t* len) {
  (void)data; (void)len;
  return false;
}

// Standard CRC-32 (IEEE 802.3), matches java.util.zip.CRC32 on the phone.
static uint32_t crc32Ieee(const uint8_t* buf, size_t len) {
  uint32_t crc = 0xFFFFFFFFu;
  for (size_t i = 0; i < len; i++) {
    crc ^= buf[i];
    for (int b = 0; b < 8; b++) crc = (crc >> 1) ^ (0xEDB88320u & (0u - (crc & 1u)));
  }
  return ~crc;
}

static void sendEvent(const uint8_t* data, size_t len) {
  eventChar->setValue(data, len);
  eventChar->notify();
}

static void sendError(uint8_t code) {
  const uint8_t pkt[2] = { EV_ERROR, code };
  sendEvent(pkt, sizeof(pkt));
}

static uint8_t batteryPercent() {
  uint32_t mv = analogReadMilliVolts(BATTERY_ADC_PIN) * 2;  // undo 1:2 divider
  if (mv <= 3300) return 0;
  if (mv >= 4200) return 100;
  return (uint8_t)((mv - 3300) * 100 / 900);
}

static void sendStatus() {
  uint8_t flags = 0;
  if (CAMERA_ENABLED) flags |= 0x01;
  if (MIC_ENABLED) flags |= 0x02;
  // bit 0x04 (charging) stays 0: no charge-detect pin wired in this reference design.
  if (ledOn) flags |= 0x08;
  const uint8_t pkt[4] = { EV_STATUS, batteryPercent(), flags, PROTOCOL_VERSION };
  sendEvent(pkt, sizeof(pkt));
}

static void captureAndSend() {
  if (!CAMERA_ENABLED) { sendError(ERR_CAMERA_NOT_READY); return; }
  const uint8_t* jpeg = nullptr;
  size_t len = 0;
  if (!cameraCaptureJpeg(&jpeg, &len) || jpeg == nullptr || len == 0 || len > 512 * 1024) {
    sendError(ERR_CAPTURE_FAILED);
    return;
  }
  const uint8_t id = frameCounter++;
  uint8_t begin[6] = { EV_FRAME_BEGIN, id, (uint8_t)len, (uint8_t)(len >> 8), (uint8_t)(len >> 16), (uint8_t)(len >> 24) };
  sendEvent(begin, sizeof(begin));

  // ATT payload = MTU - 3; our chunk header is 2 bytes (sequence number, little-endian).
  uint16_t mtu = peerMtu;
  size_t payload = (mtu > 23 ? mtu : 23) - 3 - 2;
  uint8_t packet[256];
  if (payload > sizeof(packet) - 2) payload = sizeof(packet) - 2;

  uint16_t seq = 0;
  for (size_t off = 0; off < len; off += payload, seq++) {
    size_t n = (len - off < payload) ? (len - off) : payload;
    packet[0] = (uint8_t)seq;
    packet[1] = (uint8_t)(seq >> 8);
    memcpy(packet + 2, jpeg + off, n);
    frameChar->setValue(packet, n + 2);
    frameChar->notify();
    delay(6);  // let the BLE stack drain; tune for your link
  }

  uint32_t crc = crc32Ieee(jpeg, len);
  uint8_t end[6] = { EV_FRAME_END, id, (uint8_t)crc, (uint8_t)(crc >> 8), (uint8_t)(crc >> 16), (uint8_t)(crc >> 24) };
  sendEvent(end, sizeof(end));
}

class ServerCallbacks : public NimBLEServerCallbacks {
  void onConnect(NimBLEServer*, ble_gap_conn_desc*) override {}
  void onDisconnect(NimBLEServer*) override {
    peerMtu = 23;
    NimBLEDevice::startAdvertising();
  }
  void onMTUChange(uint16_t mtu, ble_gap_conn_desc*) override { peerMtu = mtu; }
};

class CommandCallbacks : public NimBLECharacteristicCallbacks {
  void onWrite(NimBLECharacteristic* c) override {
    std::string v = c->getValue();
    if (v.empty()) return;
    if (pendingOp != 0) { sendError(ERR_BUSY); return; }
    pendingArg = v.size() > 1 ? (uint8_t)v[1] : 0;
    pendingOp = (uint8_t)v[0];
  }
};

void setup() {
  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);

  NimBLEDevice::init("SmartEyeX");
  NimBLEDevice::setMTU(247);
  // Bonding + secure connections, "just works" pairing (no display/keyboard on the glasses).
  NimBLEDevice::setSecurityAuth(true, false, true);

  NimBLEServer* server = NimBLEDevice::createServer();
  server->setCallbacks(new ServerCallbacks());
  NimBLEService* svc = server->createService(SERVICE_UUID);

  // Only an encrypted (bonded) phone may send commands.
  NimBLECharacteristic* cmd = svc->createCharacteristic(COMMAND_UUID, NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::WRITE_ENC);
  cmd->setCallbacks(new CommandCallbacks());
  eventChar = svc->createCharacteristic(EVENT_UUID, NIMBLE_PROPERTY::NOTIFY);
  frameChar = svc->createCharacteristic(FRAME_UUID, NIMBLE_PROPERTY::NOTIFY);
  svc->start();

  NimBLEAdvertising* adv = NimBLEDevice::getAdvertising();
  adv->addServiceUUID(SERVICE_UUID);
  adv->setScanResponse(true);
  adv->start();
}

void loop() {
  const uint8_t op = pendingOp;
  if (op != 0) {
    switch (op) {
      case OP_PING: { const uint8_t pkt[1] = { EV_PONG }; sendEvent(pkt, 1); break; }
      case OP_GET_STATUS: sendStatus(); break;
      case OP_SET_LED:
        ledOn = pendingArg != 0;
        digitalWrite(LED_PIN, ledOn ? HIGH : LOW);
        sendStatus();
        break;
      case OP_CAPTURE: {
        // Light the capture LED for the duration of the capture (privacy indicator).
        digitalWrite(LED_PIN, HIGH);
        captureAndSend();
        digitalWrite(LED_PIN, ledOn ? HIGH : LOW);
        break;
      }
      default: sendError(ERR_UNKNOWN_COMMAND); break;
    }
    pendingOp = 0;
  }
  delay(5);
}

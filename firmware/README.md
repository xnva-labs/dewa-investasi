# SmartEyeX glasses — BLE protocol v1 and reference firmware

`smarteyex-glasses/smarteyex-glasses.ino` is a reference firmware for an ESP32-C3 SuperMini.
It is **untested on hardware** (written without a board or compiler) — build it with
Arduino-ESP32 + **NimBLE-Arduino 1.4.x**, then validate on your board.

## What it does / does not do
- Does: advertise the SmartEyeX service, require encryption (bonding) for commands, report battery and
  LED state, answer ping, drive a capture LED, send a JPEG in CRC-checked chunks.
- Does not: drive a camera. The ESP32-C3 has no DVP camera interface; use an SPI/UART JPEG camera
  module or switch to an ESP32-S3, then implement `cameraCaptureJpeg()` and set `CAMERA_ENABLED 1`.
  Until then the glasses report `camera not ready` and the app shows exactly that.
- Does not: stream microphone audio or play audio through the bone-conduction speaker (not in protocol v1).
- Charging state is not detected (no pin in the reference design); battery % assumes a 1:2 divider on `BATTERY_ADC_PIN`
  and a linear 3.3–4.2 V map — calibrate for your cell.

## Protocol (little-endian)
Service `7e5e0001-5a1e-4e58-9c1d-0a5e7e5e0001`

| Characteristic | UUID suffix | Direction | Use |
|---|---|---|---|
| COMMAND | `7e5e0002-…` | app → glasses (write, encrypted) | `[op, args…]` |
| EVENT | `7e5e0003-…` | glasses → app (notify) | status, pong, frame begin/end, error |
| FRAME | `7e5e0004-…` | glasses → app (notify) | `[seq u16][jpeg bytes…]` |

Commands: `0x01` PING · `0x02` GET_STATUS · `0x03` CAPTURE · `0x04 [0/1]` SET_LED

Events: `0x81` PONG · `0x82 [battery%][flags][version]` STATUS (flags: 1 camera ready, 2 mic ready, 4 charging, 8 LED on) ·
`0x83 [id][len u32]` FRAME_BEGIN · `0x84 [id][crc32 u32]` FRAME_END · `0x85 [code]` ERROR
(1 camera not ready, 2 capture failed, 3 busy, 4 unknown command)

The Android side (`data/glasses/GlassesProtocol.kt`) rejects frames with gaps, wrong length or wrong CRC,
and unit tests cover the encoding. Pairing uses BLE "just works" bonding: it encrypts the link and limits
commands to the bonded phone, but it is not protection against an active attacker during first pairing.

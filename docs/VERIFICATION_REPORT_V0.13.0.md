# Zahra v0.13.0 Verification Report

## Scope

This checkpoint was produced from the exact `zahra-v0.12.0.zip` source artifact available in the conversation. The baseline `Zahra Master Build Specification v1.0` was preserved; changes are additive hardening and simulation deepening.

## Implemented in this checkpoint

- Android ↔ Godot bridge protocol v2 with authenticated, atomic, per-event transport.
- Explicit bridge direction: Android → Godot uses `inbox`; Godot → Android uses `outbox`.
- HMAC-SHA256 signatures backed by an Android Keystore key; event UUID is included in the signed message.
- Durable bridge queue bounds and Room `bridgeEventId` idempotency.
- Transactional mission completion with a unique point-ledger `sourceKey` and recurring-cycle guard.
- Reward claims checked against the live points total.
- Save integrity sidecar: compressed state hash + Android Keystore signature, plus backup recovery and repair-save flow.
- Lifecycle save hooks and reminder resynchronization after boot, clock, timezone and app replacement changes.
- Explicit Android backup/data-extraction exclusions in addition to `allowBackup=false`.
- NPC trust, goals, bounded memory, work performance and peer relationship drift.
- Dynamic economy feedback including inflation, confidence, employment, wages, purchasing pressure, supply stability, production and volatility.
- Policy budget/coalition/consultation/debt state and election/campaign consequences.
- Event Director with weighted contextual events, cooldowns and consequence chains.
- Bounded debug performance telemetry for FPS, minimum FPS, PSS, draw calls, object count, battery and temperature.
- Instrumented Android regression tests for Keystore bridge security, bridge transport direction/idempotency and Room reward idempotency.

## Verification performed in this environment

- `docs/STATIC_CHECKS.sh`: PASS.
- `docs/SOURCE_AUDIT.sh`: PASS.
- GDScript function scan: 69 functions, 0 duplicate function names.
- GDScript bare-assignment sanity scan: 0 unknown assignments.
- RewardGuard pure-JVM smoke test: PASS.
- Source diff against the exact v0.12.0 input was inspected after patching.
- Final ZIP integrity test (`zip -T`): PASS on the generated v0.13.0 source archive.
- Final source archive SHA-256: recorded in `zahra-v0.13.0.sha256`.
- Original v0.12.0 file preservation: PASS, 42/42 original files retained.

## Verification not possible here

- Android Gradle compilation and APK/AAB export: no Gradle executable and no Android SDK/platform installation are available.
- Godot parser/export/runtime: no Godot executable is available.
- Instrumented tests: test source is present, but no Android device/emulator test runtime is available.
- Physical-device bridge round trip, lifecycle crash tests and real performance/thermal/battery profiling: not executed.
- Release signing/install/upgrade verification: not executed.

## Compatibility security note

The only unsigned compressed-save path is the one-time compatibility migration for the older v0.12 format, which predates the integrity sidecar. Current v0.13 saves use a matching SHA-256 + Keystore signature pair; when current metadata is missing or invalid and a valid backup exists, the backup is preferred.

## Release interpretation

This source checkpoint is **hardened and substantially deeper**, but it is not evidence that a release APK is secure or fully ready. Runtime/device gates remain mandatory before release.

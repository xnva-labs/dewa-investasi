# Zahra v0.18.4 — QA / final source audit

## Changes in this revision
- Personal note is encrypted using AES-GCM with an Android Keystore-managed AES key. The key is not stored alongside the note.
- Existing plaintext notes remain readable; the next save writes encrypted data.
- Version bumped to `0.18.4` / `versionCode 22`.
- Carries forward the optional welcome flow, time-aware greeting, personal encouragement clearly marked as non-hadith, and persisted prayer/fasting reminder preferences from v0.18.3.

## Checks performed
- `python3 tools/audit/audit_all.py`: PASS — `Audit statis: tidak ada temuan.`
- Source tree and Gradle configuration reviewed for obvious static issues.
- No Gradle executable, Android SDK, Gradle wrapper script, or wrapper JAR is available in this environment, so Kotlin compilation, JUnit tests, APK packaging, and device/animation tests were NOT run. Do not treat the static audit as a successful Android build.

## Known limits / follow-up
- Prayer times are fetched from AlAdhan when the screen opens and should be cross-checked against a local mosque. A queued reminder can repeat the same local wall-clock time the next day; it does not yet fetch tomorrow's changed prayer times automatically after every notification. WorkManager may delay notifications due to Android battery optimization.
- Menstrual-cycle data is local but is not separately encrypted like personal notes; cycle predictions are approximate and are not medical advice or a religious ruling.
- UI animation, notification delivery, Android 13+ permission flow, and Keystore behavior need runtime tests on an emulator/device.
- CI must run `gradle testDebugUnitTest assembleDebug --stacktrace --console=plain` to confirm the actual compiler/build result.

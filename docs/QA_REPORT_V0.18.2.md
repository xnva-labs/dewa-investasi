# Zahra v0.18.2 — QA report

## Source changes
- Fixed `animateContentSize` import package (`androidx.compose.animation.animateContentSize`).
- Replaced the fully-qualified `id.fajar.zahra.prayer.PrayerTimesScreen()` call inside a Navigation Compose receiver with an explicit import and `PrayerTimesScreen()` call.
- Added the optional private cycle calendar screen and a one-shot WorkManager notification that schedules the next cycle interval after firing, avoiding daily repeat notifications.
- Added launcher icons (letter Z) for legacy densities and adaptive icon Android 8+.
- Explicitly use Asia/Jakarta when parsing Cirebon prayer times and when computing tomorrow’s prayer reminder.

## Checks performed in this workspace
- Static repository audit: passed (`tools/audit/audit_all.py`).
- Kotlin source inventory: 38 files scanned across main source and unit tests.
- XML parsing: 9 Android XML files parsed successfully.
- Duplicate import scan: none found.
- Confirmed launcher resources, manifest icon declarations, prayer route, and menstruation route.
- No duplicate or likely unused imports detected by the final import scan.
- Compiled the pure Kotlin `CyclePrediction` helper with `kotlinc` and ran a smoke test: a 28-day example produced the expected reminder date/time (2026-10-26 at 09:00), and invalid cycle lengths were rejected.
- XML parse check: 9 Android XML files passed.

## Not claimed
A full Android Gradle build / CI rerun was not available in this workspace because this extracted source package has no `gradlew` executable and no system Gradle/Android SDK installation. GitHub Actions must run `compileDebugKotlin`, `testDebugUnitTest`, and `assembleDebug` before this is called build-verified. Cycle prediction is an estimate only and is not a religious ruling or medical diagnosis.

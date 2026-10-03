# SmartEyeX Fixed15 Audit

Baseline: `smarteyex-production-root-fixed14.zip` / GitHub Actions "Android CI" run #11.

## Root cause of the failed CI run

`:app:lintDebug` aborted with 7 errors, all `NonObservableLocale` in `ReminderScreen.kt`
(`Locale.getDefault()` called directly inside composable functions: ReminderScreen header
date + clock, DateNavigator day label + day number, NowMarker, UpcomingCard).
Compile, unit tests and the production scan were already passing.

## Fixed15 changes (14 files, nothing else touched)

Lint blocker
1. `ReminderScreen.kt`: added an observable `currentLocale()` (`LocalConfiguration.current.locales[0]`)
   and used it for all 7 call sites.

Functional fixes found during the full-code review
2. `NotificationRepository.ring()`: `ToneGenerator.release()` was called right after `startTone()`,
   which cuts the beep off (DERING mode was effectively silent). Release is now delayed.
3. `PrivacySettingsScreen.kt`: screen could not scroll, so PIN, Privacy & Data and the
   "Hapus semua memori" button were cut off on normal phones. Added vertical scroll.
4. `NotificationListenerScreen.kt`: same problem (Notification Mode and Priority sections unreachable).
   Added vertical scroll (the inner feed list is already height-capped, so it is safe).
5. `XNAICoreScreen.kt`: the app is edge-to-edge, so `adjustResize` no longer applies; the chat input
   dock was hidden behind the keyboard and under 3-button navigation. Added IME + navigation-bar padding.
6. `ReminderScreen.kt` (composer sheet) and `ProfileScreen.kt`: IME padding so text fields stay visible.
7. `ReminderReceiver.kt`: SPEAK / DERING reminders finish asynchronously (TTS init, tone); the receiver
   now holds `goAsync()` for 8 s so the process is not killed before the alert is delivered.
8. `LibraryScreen.kt` / `ProgressScreen.kt`: items saved without a subject showed an empty header; now "Umum".
9. `.github/workflows/android.yml`: debug build now receives `XNAI_BASE_URL` from repository secrets
   (empty if the secret is not set), so CI APKs can reach the XNAI backend.

## Validation available here

- `scripts/production-scan.py`: PASS (`Errors: 0`).
- No Android SDK / Gradle in this environment, so Gradle compile/lint/test were NOT run locally.
  Re-run GitHub Actions on this source; lint should now pass and `assembleDebug` should produce the APK.

## Still external to the repository

- XNAI chat, Vision, Translation and the Study Planner call `POST /chat`, `/vision`, `/translate` on
  `XNAI_BASE_URL`. That backend is not part of this repository; set the `XNAI_BASE_URL` secret
  (HTTPS) once it is deployed, and turn on Cloud Processing in Privacy Control.
- Face Recognition is consent-only (no pipeline in this APK), as stated in the app.
- Physical-device QA gates from `RELEASE_CHECKLIST.md` still apply.

# SmartEyeX Fixed9 Production Audit

Baseline: `smarteyex-production-root-fixed8.zip` / GitHub run `36867090802`, job `110385172506`.

## CI baseline verified

The latest fixed8 CI run showed:

- Production static scan: PASS (`Errors: 0`).
- `:app:testDebugUnitTest`: PASS (`BUILD SUCCESSFUL`).
- Kotlin and Java compilation: PASS.
- `:app:lintDebug`: FAIL.
- Debug APK build and artifact upload were skipped because lint failed.

The lint job reported 2 errors, 34 warnings, and 5 hints. The unique error text exposed in the job log was `MissingPermission` in `ReminderReceiver.kt` at the notification call. The second unique error was not printed by the available job-log excerpt.

## Fixed9 changes

This audit package modifies 27 files relative to fixed8. The changes include:

1. Network retry loop corrected so a non-retryable or final response stops immediately. The previous `repeat { return@repeat }` form continued the loop. Response-size limiting now counts raw UTF-8 bytes instead of decoded characters.
2. Reminder notification posting now explicitly checks `POST_NOTIFICATIONS` before `notify()` and still catches a possible `SecurityException`.
3. Media session enumeration now checks Notification Access before calling `MediaSessionManager.getActiveSessions()` and safely clears stale controller state when access is revoked.
4. Compose/API deprecations were removed: `quadraticTo()` is used in place of `quadraticBezierTo()`, lifecycle owner import moved to `androidx.lifecycle.compose`, and edge-to-edge window handling is used instead of deprecated status/navigation bar color setters.
5. Reminder scheduling is restored on repository initialization and reacts to exact-alarm permission state changes. A reminder is not persisted when scheduling fails, and the UI now reports the scheduling failure instead of clearing the form.
6. Privacy reset is centralized and durable. Personal model/profile/emotion deletion uses synchronous encrypted writes/removals. Privacy consent toggles are persisted synchronously. PIN removal and voice-profile deletion are synchronous.
7. Companion profile values and synthetic emotion values are clamped to safe ranges. Companion reset restores the neutral profile/mode and clears personalization.
8. Notification quick-reply target is cleared/replaced on every notification so an older reply action cannot silently remain active after a newer non-replyable notification.
9. Voice input has a central microphone-consent gate and clears callbacks if `startListening()` fails.
10. XNAI visible chat history is bounded to 100 messages and scrolling targets the last valid index. A dead endpoint-normalization function was removed.
11. Vision lifecycle handling prevents late CameraX binding after disposal and turns the capture indicator off after bind failure. Raw camera exception text is not surfaced in the UI.
12. Emotion decay now uses an actual half-life calculation, negated emotion phrases are ignored by the simple lexical detector, and merge/decay outputs are clamped. Tests cover explicit emotion priority, neutral text, negation, punctuation, half-life, and bounded merge values.
13. Legacy privacy migration no longer clears the legacy preference store until the relevant secure values are confirmed present.

## Local validation available in this environment

- `scripts/production-scan.py`: PASS (`XML files scanned: 9`, `Kotlin files scanned: 65`, `Errors: 0`).
- Kotlin smoke compilation/execution of `EmotionEngine`: PASS, including JOY selection, negation handling, neutral text, and 15-minute half-life behavior.
- Full Android Gradle build could not be executed in this environment because a usable Gradle executable/distribution and Android dependency cache are not available.

## Known release gates still requiring external validation

- Re-run GitHub Actions on the Fixed9 source. Lint must complete, then `assembleDebug` must complete.
- Run a signed release build/AAB with real keystore and production `XNAI_BASE_URL` secrets.
- Perform physical-device QA for camera, microphone, speech recognition/TTS, Notification Access, quick reply, reminders, reboot/update recovery, app lock, revoked permissions, offline/weak-network paths, and process death.
- Validate the real XNAI backend for authentication/authorization, rate limiting, payload validation, quotas, logging, and secret isolation.
- Complete Google Play privacy/Data Safety disclosures and host the final public privacy policy.

## Exact alarm note

The app uses `SCHEDULE_EXACT_ALARM`, not `USE_EXACT_ALARM`. Android documentation says `SCHEDULE_EXACT_ALARM` is user-granted and is the broader option for user-facing precise timing features when the app is not a dedicated alarm/calendar core app. Fresh installs targeting Android 13+ generally do not receive it automatically, so the app links the user to the system Special App Access screen and falls back to inexact scheduling when it is unavailable.

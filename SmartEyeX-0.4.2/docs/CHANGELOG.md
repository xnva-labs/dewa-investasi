# SmartEyeX changelog and audit notes

Merged from the former `PRODUCTION_AUDIT.md` and `FIXED9`, `FIXED15` to `FIXED19` audit files, so each fact lives in one place. Newest first.

> Validation limit that applies to every entry from Fixed15 on: Gradle and the Android SDK were not available where these changes were written, so compile, lint, unit tests and device behavior were not run there. The authoritative check is GitHub Actions ("Android CI") plus a test on a real phone. Historical entries below describe their state at that version; newer entries supersede earlier notes.

## Fixed22: phone-first XNAI/Vision and communication hardening (0.4.2, versionCode 8)
- Added an explicit sequential Live Monitor for the phone camera with a stop control, sampled-frame analysis, optional voice feedback, and visible camera/privacy messaging.
- Translation now supports selecting source/target language, user-triggered speech-to-text input, typed input, and optional spoken output when the target language voice is installed; it remains on-demand rather than a hidden always-listening interpreter.
- Companion modes now alter the context sent to XNAI, with session-only mode selection when Memory is disabled.
- Text-to-speech queues the latest response if engine initialization has not completed; optional consented prosody adaptation now normalizes words-per-minute realistically, keeps neutral settings at normal pitch/rate, and migrates legacy saturated tempo profiles without discarding energy/language settings.
- Voice-based notification replies are staged for user review, show the captured recipient, and send only to the exact reviewed notification action. One-shot commands that name a sender resolve against that sender's recent reply action; “balas aja <message>” explicitly uses the newest target. Unknown names never silently send to the latest person. Text quick-reply RemoteInput is checked before use.
- Mic Live now respects notification delivery mode while active instead of speaking for GETAR/DERING/SENYAP; companion SENYAP consistently suppresses automatic spoken readout. The listener prefers actions with text quick-reply inputs, and turning voice-reply OFF blocks even a draft already waiting for review.
- One-shot voice commands can ask to read notifications aloud using natural variants such as “bacakan pesan”; TTS queues up to ten messages during engine startup rather than dropping earlier ones. Reply aliases such as “jawab pesan” are also covered by routing tests.
- Added spoken app-opening route tests; tightened provider-call retry behavior so requests without an idempotency contract are not automatically retried.
- Validation: production static scan passes. Gradle/Android SDK are absent in this environment, so compile, lint, unit tests and real-device behavior remain unverified. Live Monitor's `/vision` prompt semantics require the separate deployed XNAI backend to honor the documented request fields.

## Fixed21: production release gate and truthful feature status
- Public release builds no longer embed `GEMINI_API_KEY`; the signed AAB requires an HTTPS XNAI backend so provider credentials stay server-side.
- Release CI validates `XNAI_BASE_URL` as mandatory; debug builds may still use Gemini for development testing.
- Removed the Face Recognition consent switch because this source does not contain a face-recognition pipeline; the Device screen now reports it as unavailable instead of suggesting it works.
- Bumped Android version to 0.4.1 (versionCode 7).
- Validation limitation: this environment has no Gradle executable/Android SDK, so Gradle compile, lint, and unit tests cannot be truthfully marked as passed. Run the CI workflow and physical-device QA before public launch.

## Fixed20: cleanup and hardening
- Removed duplicate code: `canScheduleExact` (was copied in `DeviceScreen` and `ReminderScreen`) is now `ReminderRepository.canScheduleExactAlarms`; the reminder beep preview (`previewTone`) now reuses `NotificationRepository.ring`.
- Removed 8 unused imports.
- Merged 7 audit documents into this file.
- Mic Live: watchdogs so the service cannot stay deaf (recognizer never answers) or mute (speech engine never reports completion).
- Static review of all 78 Kotlin files: no duplicate imports, no missing imports for names or extensions, no unknown members called on project objects, no unused members, no duplicate declarations in a package, manifest components and permissions consistent with the code.

## Fixed19: Gemini direct
- `data/gemini/` client for chat (system instruction + history), image description and translation. Used when `XNAI_BASE_URL` is empty and `GEMINI_API_KEY` is set at build time; model alias `gemini-flash-latest`, override with the `GEMINI_MODEL` variable.
- `X-Android-Package` / `X-Android-Cert` headers so an Android-app restriction on the key keeps working.
- CI passes `GEMINI_API_KEY` and `GEMINI_MODEL`; release accepts either an XNAI URL or a Gemini key.
- First launch goes to Profile once before the dashboard.
- Fix: the SmartEyeX soul was not sent when Memory was OFF. It is now always sent; only personalization depends on Memory.
- Security: the key is compiled into the APK. Keep the repository private (debug APKs are workflow artifacts), restrict the key to the package and signing SHA-1, set a quota alert.

## Fixed18: soul and cleanup of the XNAI backend
- Removed the reference `backend/` and its workflow. XNAI is a separate project; the app still calls `POST /chat`, `/vision`, `/translate` on `XNAI_BASE_URL`.
- `SoulPrompt`: the fixed personality is sent with every cloud request.
- Mic Live replies now need confirmation: "jawab <nama> <isi>" is read back and sent only after "iya".
- Mic Live stays quiet for incoming messages in SENYAP mode.
- `docs/AUDIT_ALUR_DAN_JIWA.md`: app flow and conformance with the soul blueprint.

## Fixed17: Mic Live
- Microphone foreground service with the wake word "SmartEyeX", per-sender reply targets, "buka <app>", reading new messages aloud, questions answered by voice.
- Android limits that are not bugs: permanent notification and mic indicator cannot be hidden; the service must be started while the app is visible (Android 14+); launching another app from the background is blocked, so a tap-to-open notification is shown instead.

## Fixed16: publication support
- Public privacy policy draft (`docs/privacy-policy.html`), version bump, release notes.

## Fixed15: CI repair
- Root cause of the failed run: 7 `NonObservableLocale` lint errors in `ReminderScreen.kt`. Fixed with an observable locale.
- Other fixes: DERING beep was cut off (`ToneGenerator.release()` too early); Privacy and Notification Listener screens could not scroll; XNAI chat input hidden behind keyboard/navigation bar (IME and navigation-bar padding); reminder SPEAK/DERING now hold `goAsync()`; empty subject label in Library/Progress shows "Umum"; CI debug build receives `XNAI_BASE_URL`.

## Fixed9: lint and robustness (from run 36867090802)
- Network retry loop corrected; response-size limit counts raw bytes.
- Reminder notification checks `POST_NOTIFICATIONS` before `notify()`; media sessions check Notification Access first.
- Compose/API deprecations removed (`quadraticTo`, lifecycle owner import, edge-to-edge).
- Reminder scheduling restored at init and reacts to exact-alarm permission changes; a reminder is not saved if scheduling fails.
- Privacy reset centralized and durable (synchronous encrypted writes); consent toggles, PIN removal and voice-profile deletion are synchronous.
- Companion and emotion values clamped; companion reset restores the neutral profile.
- Quick-reply target cleared or replaced on every notification; microphone consent gate for voice input; XNAI chat history bounded to 100 messages; Vision lifecycle fixes; emotion decay uses a real half-life and ignores negated phrases; legacy privacy migration is safe.

## Architecture notes (from the former production audit)
- `scripts/production-scan.py` runs in CI: XML validity, Kotlin delimiter sanity, resource references, merge-conflict markers, obvious key literals.
- Voice-first layer with local intent routing; companion modes and profile; bounded synthetic emotional state (no claim of consciousness); encrypted personal model; optional voice prosody personalization that stores bounded statistics, not audio.
- Boundary: Android TTS can adapt pitch and rate but cannot be claimed to clone a voice. A consented voice-synthesis service would be required before making that claim.
- Emotion inference is a keyword heuristic, not a validated detector (no reliable handling of sarcasm or context).
- The app uses `SCHEDULE_EXACT_ALARM` (user-granted). Without it, reminders fall back to inexact scheduling and the app links to the system Special App Access screen.
- Release gates that always need external validation: signed release build with real keystore, physical-device QA (camera, microphone, TTS, Notification Access, quick reply, reminders, reboot recovery, app lock, revoked permissions, weak network), Google Play Data Safety, and a hosted privacy policy URL.

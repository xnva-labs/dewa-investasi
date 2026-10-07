> Note: the `backend/` folder described below was removed in Fixed18 (XNAI becomes a separate project).

# SmartEyeX Fixed17: Mic Live (always-on voice)

Baseline: fixed16 (`FIXED15_AUDIT.md`, `FIXED16_AUDIT.md`). Version 0.4.0 (versionCode 6).

## New
- `service/ListeningService.kt`: microphone foreground service. Continuous speech recognition (id-ID, prefers offline), wake word "SmartEyeX" (variants such as "Smart Eyes" accepted), pauses while speaking so it never hears itself.
- Commands after the wake word: `matikan mic`, `buka <app>`, `bacakan notifikasi`, `jawab <nama> <isi>`, anything else is a question to XNAI answered by voice (needs Cloud Processing).
- Reply by sender: `NotificationRepository` now remembers the reply action per sender (max 20, 30 min, memory only). New messages are read aloud while Mic Live runs; "jawab dek zaa ..." answers the matching sender. The shortcut works without the wake word only for 2 minutes after that sender's message and only on a name match; unknown or ambiguous names are never guessed.
- `data/assistant/*` (pure JVM, unit tests in `AssistantCommandParserTest`), `data/apps/AppLauncher.kt`.
- UI: tap VOICE on the XNAI status bar to start/stop (first start shows a disclosure), notification button "Matikan mic", `SmartEyeX matikan mic`.
- Manifest: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `WAKE_LOCK`, service with `foregroundServiceType="microphone"`, `<queries>` for launcher apps (no QUERY_ALL_PACKAGES).
- Policy: in-app Privacy Policy and `docs/privacy-policy.html` updated for always-on mic.

## Limits that are Android rules, not bugs
- The permanent notification and the system mic indicator cannot be hidden.
- The service can only be started while SmartEyeX is on screen (Android 14+); after a reboot or if the system kills it, tap VOICE again.
- Opening another app directly works only while SmartEyeX is visible. From the background Android blocks it, so a tap-to-open notification is posted. A default-assistant role is the next step if fully hands-free launch is required.
- Instagram usually does not expose a reply action in its notifications, so replies may not be possible there.
- Some devices play a beep each time the recognizer restarts.
- Live camera video is not part of this build.

## Validation
- Parser/matcher behaviour was checked with an equivalent Python port (all cases pass); `node --test` backend 10/10; `production-scan.py` PASS.
- Gradle/Android SDK are not available here: lint, compile and unit tests have NOT been run. Re-run GitHub Actions.

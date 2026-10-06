# SmartEyeX

Wearable AI assistant companion app (XNVA Labs), native Android with Kotlin + Jetpack Compose.

## Production baseline
- Android target/compile SDK 36
- Minimum Android 12 / API 31
- Encrypted local sensitive storage backed by Android Keystore
- HTTPS-only cloud transport
- Runtime privacy gates for camera, microphone, notifications, memory and cloud processing
- Hardened PIN authentication with PBKDF2 + legacy PIN migration
- Real voice input/TTS and notification quick-reply support when the Android notification exposes a remote input action
- Release shrinking via R8 and CI unit/lint checks

## Structure
`app/src/main/java/com/xnvalabs/smarteyex/`
- `MainActivity.kt` — entry point + application shell
- `SmartEyeXApplication.kt` — process-wide repository initialization
- `core/` — secure storage, networking, diagnostics, voice controller
- `ui/theme`, `ui/components`, `ui/screens/*` — Compose UI
- `service/` — NotificationListenerService, ReminderReceiver, BootReceiver
- `data/*` — repositories for privacy, memory, XNAI, vision, reminders, notifications, etc.

## Build
Open the project in Android Studio with an Android SDK that supports API 36, or use Gradle directly:

```bash
gradle :app:testDebugUnitTest
gradle :app:lintDebug
gradle :app:assembleDebug
```

GitHub Actions runs unit tests, lint and a debug build on pushes and pull requests.

## XNAI backend
Set the build environment variable `XNAI_BASE_URL` to the production API origin, for example `https://api.example.com`.
The client calls:
- `POST /chat`
- `POST /vision`
- `POST /translate`

The app rejects non-HTTPS cloud endpoints and does not embed API secrets in the APK.

For release, the GitHub Actions workflow expects repository secrets `XNAI_BASE_URL`, `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`.

A reference implementation of these endpoints (Node, tests, Dockerfile, Cloud Run steps) is in `backend/`; see `backend/README.md`.

## Privacy publication
The in-app privacy disclosure mirrors the current controls, but a public privacy-policy webpage must also be hosted by XNVA (a ready draft is in `docs/privacy-policy.html`; fill the highlighted placeholders, then host it, for example with GitHub Pages from `/docs`) and supplied to Google Play before publication. The Play Console Data Safety declaration must match the app's actual data practices.

## XNAI Companion Intelligence

SmartEyeX is voice-first: voice is the primary interaction surface while physical UI remains available for privacy, setup, visual status, and safety-critical confirmation.

The companion layer includes:
- encrypted personal model for preferences and goals;
- bounded synthetic emotional state for expressive behavior, without claiming consciousness;
- companion modes: Friend, Teacher, Mentor, Researcher, Engineer, Coach, Silent;
- reasoning profiles for philosophy, science, mathematics, engineering, invention, teaching, and research;
- local voice-command intent routing with explicit permission checks;
- optional prosody personalization that stores only bounded statistics, not raw audio;
- contextual learning from interactions without retraining the foundation model on every conversation.

All personalization remains permission-controlled and deletable.

## Mic Live (always-on voice)

Tap **VOICE** on the XNAI screen (first time shows a disclosure). A microphone foreground service listens continuously, with a permanent notification and Android's mic indicator.

- Wake word: say "SmartEyeX ..." (variants like "Smart Eyes" are accepted). Other speech is discarded immediately.
- "SmartEyeX matikan mic", the notification button, or the VOICE tile stop it.
- "SmartEyeX buka <app>" only opens the app. From the background Android blocks direct launches, so a tap-to-open notification is shown instead.
- Incoming WhatsApp/Messenger-style notifications that offer a reply action are read aloud; answer with "jawab <sender name> <message>" (works without the wake word for 2 minutes after the message).
- Questions after the wake word go to XNAI (Cloud Processing must be ON) and are answered by voice.

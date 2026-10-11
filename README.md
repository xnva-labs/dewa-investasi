# SmartEyeX

Wearable AI assistant companion app (XNVA Labs), native Android with Kotlin + Jetpack Compose.

## Production baseline
- Android target/compile SDK 36
- Minimum Android 12 / API 31
- Encrypted local sensitive storage backed by Android Keystore
- HTTPS-only cloud transport
- Runtime privacy gates for camera, microphone, notifications, memory and cloud processing
- Hardened PIN authentication with PBKDF2 + legacy PIN migration
- Real voice input/TTS, explicit Live Monitor camera sampling, and notification quick-reply support when the Android notification exposes a text remote-input action
- Release shrinking via R8 and CI unit/lint checks

## Structure
`app/src/main/java/com/xnvalabs/smarteyex/`
- `MainActivity.kt` — entry point + application shell
- `SmartEyeXApplication.kt` — process-wide repository initialization
- `core/` — secure storage, networking, diagnostics, voice controller
- `ui/theme`, `ui/components`, `ui/screens/*` — Compose UI
- `service/` — NotificationListenerService, microphone foreground service, reminder and boot receivers
- `data/*` — repositories for privacy, memory, XNAI, vision, reminders, notifications, translation, etc.

Translation supports typed or explicitly triggered speech input plus optional spoken results. It is not a continuous interpreter; the user reviews recognized text and presses Translate before the text is sent to cloud AI.

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

For `POST /vision`, the request includes `imageBase64`, `mimeType`, `purpose` (`snapshot` or `live_monitor`), an `instruction`, and optionally `previousObservation`. The backend must use/validate the instruction and return a JSON `description` field. Live Monitor is an explicit, sequential sample every ~3 seconds—not a continuous video stream—and stops on request/error or when camera/cloud consent is disabled. Camera frames are sent to the configured HTTPS service during analysis; do not run it around private content without appropriate consent.

The app rejects non-HTTPS cloud endpoints and does not embed API secrets in the APK.

For release, the GitHub Actions workflow expects repository secrets `XNAI_BASE_URL` (HTTPS production backend), `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`. The release workflow intentionally fails if the backend URL is missing.

The XNAI backend is a separate project and is not part of this repository.

### Cloud brain: XNAI server or direct Gemini
- `XNAI_BASE_URL` (HTTPS) set: the app talks to that server (`POST /chat`, `/vision`, `/translate`).
- Development/debug builds may use `GEMINI_API_KEY` directly for local testing (optional `GEMINI_MODEL`, default alias `gemini-flash-latest`). The SmartEyeX "jiwa" is sent as the system instruction either way.
- **Public release builds do not embed `GEMINI_API_KEY`.** A Google Play release requires `XNAI_BASE_URL` pointing to a deployed HTTPS backend. Keep provider credentials on the backend, where authentication, rate limiting, quotas, and abuse controls can be enforced.

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

## XNAI and phone Vision

- The companion selector (Friend, Teacher, Mentor, Researcher, Engineer, Coach, Silent) contributes role guidance to XNAI requests. Personalization persistence follows the Memory consent setting.
- Snapshot Vision uses the phone's CameraX camera. Live Monitor captures and analyzes frames one at a time with a visible active-camera state and an explicit Stop action; voice feedback can be disabled.
- Vision is advisory only; image frames cannot reliably establish RPM, exact ingredient quantities, temperature, or machine safety.

## Mic Live (always-on voice)

Tap **VOICE** on the XNAI screen (first time shows a disclosure). A microphone foreground service listens continuously, with a permanent notification and Android's mic indicator.

- Wake word: say "SmartEyeX ..." (variants like "Smart Eyes" are accepted). Other speech is discarded immediately.
- "SmartEyeX matikan mic", the notification button, or the VOICE tile stop it.
- "SmartEyeX buka <app>" only opens the app. From the background Android blocks direct launches, so a tap-to-open notification is shown instead.
- Incoming messages are read aloud only when Notification Mode is SPEAK and Companion Mode is not SENYAP; GETAR/DERING/SENYAP respect the selected delivery mode. Say “bacain notifikasi” to read the recent feed aloud from one-shot voice control (unless Companion Mode is SENYAP). Voice replies require the preference enabled and a notification with a text reply action. Say "jawab <sender name> <message>" (works without the wake word for 2 minutes after the message); Mic Live reads the proposed text back and sends only after a clear confirmation. One-shot voice reply drafts also require an explicit on-screen Send tap, resolve named senders to their exact recent reply action, and are blocked if Voice Reply is turned off before sending. "Balas aja <message>" explicitly targets the newest reply-capable notification.
- Questions after the wake word go to XNAI (Cloud Processing must be ON) and are answered by voice.

## Change history
Audit notes and the list of changes per version are in `docs/CHANGELOG.md`. The app flow and the conformance check against the SmartEyeX soul are in `docs/AUDIT_ALUR_DAN_JIWA.md`.

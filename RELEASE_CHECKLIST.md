# SmartEyeX Release Checklist

Use this checklist before creating a public Play Store release.

## Build and signing
- [ ] Run `gradle clean test lintDebug assembleDebug` in an Android SDK environment, or run the equivalent tasks from Android Studio.
- [ ] Configure `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` in CI.
- [ ] Verify the release AAB is signed with the intended upload key.

## Backend
- [ ] Deploy `XNAI_BASE_URL` as a production HTTPS API. A backend is mandatory for release; provider API keys must never be embedded in the public APK/AAB.
- [ ] Verify authentication, rate limiting, payload validation, quotas, timeouts, and server-side logging.
- [ ] Verify backend never returns secrets or private model credentials to the APK.

## Privacy and Play
- [ ] Publish the final privacy policy on a stable public HTTPS URL.
- [ ] Match Play Console Data Safety declarations to the actual production data flows.
- [ ] Review microphone, camera, notification access, and memory disclosures.
- [ ] Verify in-app controls actually stop collection/processing when disabled.

## Device QA
- [ ] Test Android 12 through the current supported Android release on low-, mid-, and high-range devices.
- [ ] Test microphone, speech recognition, TTS, camera lifecycle, Vision snapshot and Live Monitor stop/error paths, notification delivery modes, voice-reply review and confirmation, reminders, reboot recovery, and app auto-lock.
- [ ] Test airplane mode, weak network, backend timeout, denied permissions, revoked permissions, storage pressure, and process death.

## Release observability
- [ ] Connect a production crash-reporting/monitoring service.
- [ ] Verify crash reports and diagnostics never include raw private user content, notification bodies, camera frames, or API secrets.
- [ ] Define an incident-response and rollback path before public rollout.

## Companion / Voice Intelligence
- [ ] Verify voice personalization consent is OFF by default.
- [ ] Verify disabling voice personalization deletes its stored prosody profile.
- [ ] Verify notification, camera, memory, cloud, and voice personalization permissions independently gate their data paths.
- [ ] Verify voice commands never execute sensitive actions without the required confirmation/permission.
- [ ] Verify personal model can be cleared and no deleted memory is sent to cloud context.
- [ ] Verify companion responses never claim subjective consciousness or manipulate user dependency.
- [ ] Verify prosody adaptation does not persist raw microphone audio.
- [ ] Verify production backend validates reasoning/companion fields server-side and enforces user authorization.

## Mic Live (always-on voice) QA
- [ ] Start from the VOICE tile on the XNAI screen: the permanent "SmartEyeX sedang mendengarkan" notification appears and the system mic indicator is on.
- [ ] Say "SmartEyeX matikan mic", tap the notification's "Matikan mic" button, and tap VOICE again: each stops the service.
- [ ] Set Notification Mode to SPEAK and Companion Mode away from SENYAP, then lock the screen: incoming messages are read aloud; "jawab <name> <text>" is repeated back and sent only after a clear "iya" confirmation.
- [ ] "SmartEyeX buka <app>" opens the app while SmartEyeX is on screen, and shows a tap-to-open notification from the background.
- [ ] Turning Microphone OFF in Privacy Control stops the service within a few seconds.
- [ ] Declare the microphone foreground service and the always-on use case in Play Console (foreground service permissions and Data safety).

## Gemini direct (if no XNAI server)
- [ ] `GEMINI_API_KEY` secret set; repository is private; key restricted to this app (package + SHA-1 of the release signing key) and has a quota/budget alert.
- [ ] `GEMINI_MODEL` variable set if the default alias `gemini-flash-latest` is not available for your key.
- [ ] Chat, camera analysis, translation and a Mic Live question each answer on a real device with Cloud Processing ON.
- [ ] Privacy policy states the Gemini data terms of the tier you use (free vs paid).

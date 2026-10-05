# SmartEyeX Release Checklist

Use this checklist before creating a public Play Store release.

## Build and signing
- [ ] Run `gradle clean test lintDebug assembleDebug` in an Android SDK environment, or run the equivalent tasks from Android Studio.
- [ ] Configure `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` in CI.
- [ ] Verify the release AAB is signed with the intended upload key.

## Backend
- [ ] Set `XNAI_BASE_URL` to the production HTTPS API.
- [ ] Verify authentication, rate limiting, payload validation, quotas, timeouts, and server-side logging.
- [ ] Verify backend never returns secrets or private model credentials to the APK.

## Privacy and Play
- [ ] Publish the final privacy policy on a stable public HTTPS URL.
- [ ] Match Play Console Data Safety declarations to the actual production data flows.
- [ ] Review microphone, camera, notification access, and memory disclosures.
- [ ] Verify in-app controls actually stop collection/processing when disabled.

## Device QA
- [ ] Test Android 12 through the current supported Android release on low-, mid-, and high-range devices.
- [ ] Test microphone, speech recognition, TTS, camera lifecycle, notifications, quick reply, reminders, reboot recovery, and app auto-lock.
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

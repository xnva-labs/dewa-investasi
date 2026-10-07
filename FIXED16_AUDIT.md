> Note: the `backend/` folder described below was removed in Fixed18 (XNAI becomes a separate project).

# SmartEyeX Fixed16

Baseline: `smarteyex-production-root-fixed15.zip` (fixed15 fixed the 7 `NonObservableLocale` lint errors and several UI/runtime bugs; see `FIXED15_AUDIT.md`).

## Added
- `backend/`: reference XNAI server for `POST /chat`, `/vision`, `/translate` (+ `/healthz`). Zero dependencies, Node 20+, Dockerfile, 10 passing tests against a mock upstream, input validation, per-IP rate limit, daily upstream cap, idempotent retries, no body logging.
- `.github/workflows/backend.yml`: runs the backend tests when `backend/` changes.
- `docs/privacy-policy.html`: public privacy policy draft matching the app's real data flows. Two highlighted placeholders must be filled before publishing (contact email, AI-provider note).

## Changed
- `versionCode` 4 -> 5, `versionName` 0.3.1 -> 0.3.2 (Play Console rejects a repeated versionCode).
- `README.md`: pointers to the backend and the policy page.

## Not changed (needs your decision)
- App code is untouched in this round.
- The in-app text "Privacy & Data" says reminders are not stored as plain text. In fact `ReminderRepository` also keeps a recovery copy in plain app-private SharedPreferences (`smarteyex_reminder_state`). The public policy describes this accurately; the in-app text should be aligned.
- The app sends no auth token to the backend (see `backend/README.md`, "Known limits").

## Validation
- `node --test` in `backend/`: 10/10 pass. `scripts/production-scan.py`: PASS.
- Gradle/Android not available here: re-run GitHub Actions to confirm lint, unit tests and `assembleDebug`.

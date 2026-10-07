# SmartEyeX Fixed18

Baseline: Fixed17 (Mic Live). Version 0.4.0.

## Removed
- `backend/` (reference XNAI server) and `.github/workflows/backend.yml`. XNAI is a separate project now. The app client is unchanged and still calls `XNAI_BASE_URL`.

## Changed
- `SoulPrompt` (new): the fixed personality, sent inside the companion context of every XNAI request. `CompanionRepository.companionContext()` now puts it first, so truncation can only cut the optional user summary.
- Mic Live replies now require confirmation: "jawab <nama> <isi>" is read back and sent only after "iya"; "batal" or a new wake-word command cancels; the window is 20 s.
- Mic Live stays quiet for incoming messages when the notification mode is SENYAP.
- `docs/privacy-policy.html`: no longer states how the XNAI server behaves or names an AI provider; three highlighted placeholders to fill.
- README: backend pointer replaced by "separate project".

## Added
- `docs/AUDIT_ALUR_DAN_JIWA.md`: app flow, per-feature check, conformance matrix against the soul blueprint.
- Unit tests: `SoulPromptTest`, confirmation cases in `AssistantCommandParserTest`.

## Validation
- Parser and confirmation logic mirrored in Python: all cases pass. `scripts/production-scan.py`: PASS.
- Gradle/Android SDK not available here: lint, compile and unit tests have NOT been run. Re-run GitHub Actions.

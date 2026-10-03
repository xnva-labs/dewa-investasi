# SmartEyeX fixed16 implementation notes

- Removed build-time `XNAI_BASE_URL` injection from Gradle and Android CI.
- Added `xnai-config.json`, fetched from the public raw file URL in `XnaiRepository`. It stores the HTTPS backend base URL only.
- Chat, Vision, and Translation now refresh the endpoint from the JSON before calling the service.
- No model API key or user data belongs in the public JSON.
- `backend/README.md` documents the hosting boundary: GitHub static hosting and Actions are not a continuously running AI API. `backendBaseUrl` must be set after deploying an HTTPS backend elsewhere. With the template's empty value, cloud calls return a clear configuration error.
- Face identity recognition is not activated in this revision. The existing camera preview / server-side Vision analysis is not face identification. A safe implementation needs on-device face detection plus explicit enrollment, local encrypted face templates, match thresholds, deletion controls, and runtime-camera permission. The source does not yet implement that identity pipeline; the UI must not claim otherwise.

## Verification boundary
The static integrity scan is run during preparation. Android Gradle compilation, instrumentation tests, and physical-device validation must still run in GitHub Actions / on-device before release.

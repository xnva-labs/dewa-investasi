# XNAI backend deployment boundary

`xnai-config.json` is a public endpoint configuration served from this repository's `main` branch. Android fetches it at runtime, so no `XNAI_BASE_URL` Gradle environment variable or GitHub Actions secret is needed to compile the app.

This JSON is **not an AI backend**. GitHub Pages/raw file hosting serves static content and GitHub Actions is a finite CI/automation runner, not a continuously available HTTPS API. The value `backendBaseUrl` must point to an actual deployed HTTPS service that implements `POST /chat`, `POST /vision`, and `POST /translate` with the response fields expected by the app. The backend should keep model-provider credentials in its own server-side secret store, enforce authentication/rate limits, and avoid logging private prompts/images.

Until a backend is deployed and its URL is placed in `xnai-config.json`, XNAI cloud requests correctly return a configuration error; this change does not claim hosted inference is live.

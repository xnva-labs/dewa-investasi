# XNAI backend (reference)

Zero-dependency Node 20+ server for the three endpoints the SmartEyeX app calls.
It forwards to the Anthropic Messages API, so the API key stays on the server, never in the APK.

| Endpoint | Request (from the app) | Response |
|---|---|---|
| `POST /chat` | `message`, `history[]`, `thinkMode`, `reasoning`, `companion`, `context` | `{ "reply": "..." }` |
| `POST /vision` | `imageBase64` (JPEG, max 2 MB), `mimeType` | `{ "description": "..." }` |
| `POST /translate` | `text`, `targetLanguage` | `{ "translated": "..." }` |
| `GET /healthz` | | `{ "ok": true, "llm": true }` |

Errors are always `{ "message": "..." }`. Upstream overload returns `503`, which the app retries with the same `Idempotency-Key`; a 5-minute in-memory cache makes those retries cost one upstream call.

## Run locally

```bash
cd backend
ANTHROPIC_API_KEY=your-key node server.mjs
curl -s localhost:8080/chat -H 'content-type: application/json' -d '{"message":"halo"}'
npm test        # 10 tests against a mock upstream, no key needed
```

## Deploy on Google Cloud Run (example)

```bash
printf '%s' "$ANTHROPIC_API_KEY" | gcloud secrets create anthropic-api-key --data-file=-
gcloud run deploy xnai-backend --source backend --region asia-southeast2 \
  --allow-unauthenticated --max-instances 2 \
  --set-env-vars TRUST_PROXY=1 --set-secrets ANTHROPIC_API_KEY=anthropic-api-key:latest
```

Then set the GitHub secret `XNAI_BASE_URL` to the service URL (HTTPS, no trailing path), and turn on Cloud Processing in the app.
Any host that runs a container or `node server.mjs` works; a `Dockerfile` is included.

## Configuration

| Variable | Default | Notes |
|---|---|---|
| `ANTHROPIC_API_KEY` | required | Keep it in your host's secret store. |
| `XNAI_MODEL` | `claude-sonnet-5-5` | Also `XNAI_VISION_MODEL` for `/vision`. |
| `RATE_LIMIT_PER_MIN` | `20` | Per client IP, per instance. |
| `DAILY_REQUEST_CAP` | `2000` | Hard cap on upstream calls per UTC day, per instance. |
| `TRUST_PROXY` | `0` | `1` behind a trusted proxy so the real client IP is used. |

## Known limits (read before launch)

- **No per-user authentication.** The current app sends no token, so the endpoints are public. Cost is bounded by the per-IP limit and the daily cap (both per instance, hence `--max-instances 2`), and you should also set a spend limit and alert on the Anthropic key. Real per-user protection needs an app change (for example Play Integrity or sign-in), not just a server change.
- Rate-limit and idempotency state is in memory and resets on restart.
- If you switch AI provider, update the "Penyedia model AI" section of `docs/privacy-policy.html`.

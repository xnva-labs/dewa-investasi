import http from "node:http";
import { randomUUID } from "node:crypto";
import { pathToFileURL } from "node:url";
import { HttpError, parseChat, parseTranslate, parseVision } from "./validate.mjs";
import { DailyBudget, IdempotencyCache, TokenBucket } from "./limits.mjs";
import { createProvider } from "./providers.mjs";
import { normalizeBand } from "./persona.mjs";
import { cleanForVoice, crisisFallback, detectCrisisInConversation } from "./safety.mjs";

const JSON_BODY_LIMIT = 64 * 1024;
const VISION_BODY_LIMIT = 3_300_000;
const DEVICE_ID = /^[A-Za-z0-9-]{16,64}$/;
const IDEMPOTENCY_KEY = /^[A-Za-z0-9._:-]{8,100}$/;

function log(entry) {
  // Never log request bodies, prompts, images, or model output; only routing metadata.
  console.log(JSON.stringify({ ts: new Date().toISOString(), ...entry }));
}

function send(res, status, payload, extraHeaders = {}) {
  const body = JSON.stringify(payload);
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "content-length": Buffer.byteLength(body),
    "cache-control": "no-store",
    "x-content-type-options": "nosniff",
    ...extraHeaders,
  });
  res.end(body);
}

async function readJson(req, limit) {
  const type = String(req.headers["content-type"] || "").toLowerCase();
  if (!type.startsWith("application/json")) throw new HttpError(415, "Content-Type harus application/json.");
  const declared = Number(req.headers["content-length"]);
  if (Number.isFinite(declared) && declared > limit) throw new HttpError(413, "Permintaan terlalu besar.");
  const chunks = [];
  let total = 0;
  for await (const chunk of req) {
    total += chunk.length;
    if (total > limit) throw new HttpError(413, "Permintaan terlalu besar.");
    chunks.push(chunk);
  }
  try {
    return JSON.parse(Buffer.concat(chunks).toString("utf8"));
  } catch {
    throw new HttpError(400, "JSON tidak valid.");
  }
}

export function createApp({
  provider = createProvider(),
  env = process.env,
  now = () => Date.now(),
} = {}) {
  const trustProxy = env.TRUST_PROXY === "1";
  const perDevice = new TokenBucket({ capacity: Number(env.RATE_DEVICE_BURST) || 20, refillPerSecond: Number(env.RATE_DEVICE_PER_SEC) || 0.5, now });
  const perIp = new TokenBucket({ capacity: Number(env.RATE_IP_BURST) || 60, refillPerSecond: Number(env.RATE_IP_PER_SEC) || 1, now });
  const budget = new DailyBudget({ cap: env.XNAI_DAILY_CAP === undefined ? 2000 : Number(env.XNAI_DAILY_CAP), now });
  const idempotency = new IdempotencyCache({ now });

  const routes = {
    "/chat": { limit: JSON_BODY_LIMIT, parse: parseChat, run: (input) => provider.chat(input), field: "reply", bands: ["ADULT", "TEEN"] },
    "/translate": { limit: JSON_BODY_LIMIT, parse: parseTranslate, run: (input) => provider.translate(input), field: "translated", bands: ["ADULT", "TEEN"] },
    // Camera frames can contain bystanders; cloud vision is adults-only.
    "/vision": { limit: VISION_BODY_LIMIT, parse: parseVision, run: (input) => provider.vision(input), field: "description", bands: ["ADULT"] },
  };

  function clientIp(req) {
    if (trustProxy) {
      const forwarded = String(req.headers["x-forwarded-for"] || "").split(",").map((s) => s.trim()).filter(Boolean);
      if (forwarded.length) return forwarded[forwarded.length - 1];
    }
    return req.socket.remoteAddress || "unknown";
  }

  const server = http.createServer(async (req, res) => {
    const started = now();
    const requestId = randomUUID().slice(0, 8);
    const url = new URL(req.url || "/", "http://localhost");
    let status = 500;
    try {
      if (req.method === "GET" && url.pathname === "/health") {
        status = 200;
        return send(res, 200, { status: "ok", provider: provider.name });
      }
      const route = routes[url.pathname];
      if (!route) throw new HttpError(404, "Endpoint tidak ditemukan.");
      if (req.method !== "POST") throw new HttpError(405, "Gunakan metode POST.", { allow: "POST" });

      const deviceId = String(req.headers["x-device-id"] || "");
      if (!DEVICE_ID.test(deviceId)) throw new HttpError(400, "Header X-Device-Id wajib dan harus valid.");

      for (const [bucket, key] of [[perIp, clientIp(req)], [perDevice, deviceId]]) {
        const verdict = bucket.take(key);
        if (!verdict.allowed) {
          status = 429;
          return send(res, 429, { message: "Terlalu banyak permintaan. Coba lagi sebentar." }, { "retry-after": String(verdict.retryAfterSeconds) });
        }
      }

      // Self-declared by the app (it only knows a birth year). Missing/unknown is treated as TEEN, the safer profile.
      const ageBand = normalizeBand(req.headers["x-age-band"]);
      if (ageBand === "CHILD") throw new HttpError(403, "Layanan ini untuk usia 13 tahun ke atas.");
      if (!route.bands.includes(ageBand)) throw new HttpError(403, "Fitur ini belum tersedia untuk usia kamu.");

      const parsed = route.parse(await readJson(req, route.limit));
      const crisis = url.pathname === "/chat" && detectCrisisInConversation(parsed.message, parsed.history);
      const input = { ...parsed, ageBand, crisis };
      const rawKey = String(req.headers["idempotency-key"] || "");
      const key = IDEMPOTENCY_KEY.test(rawKey) ? `${deviceId}:${url.pathname}:${rawKey}` : null;

      const payload = await idempotency.run(key, async () => {
        // A person in crisis must always get an answer, even if the budget is spent or the model is down.
        if (!budget.tryUse()) {
          if (crisis) return { reply: crisisFallback(ageBand), safety: "crisis" };
          throw new HttpError(503, "Kuota harian layanan AI habis. Coba lagi besok.");
        }
        try {
          const text = await route.run(input);
          const out = { [route.field]: url.pathname === "/chat" ? cleanForVoice(text) : text };
          if (crisis) out.safety = "crisis";
          return out;
        } catch (error) {
          budget.refund();
          if (crisis) return { reply: crisisFallback(ageBand), safety: "crisis" };
          throw error;
        }
      });
      status = 200;
      return send(res, 200, payload);
    } catch (error) {
      if (error instanceof HttpError) {
        status = error.status;
        return send(res, error.status, { message: error.message }, error.headers);
      }
      status = 500;
      log({ level: "error", event: "unhandled", requestId, name: error?.name });
      return send(res, 500, { message: "Terjadi kesalahan di server." });
    } finally {
      log({ level: "info", event: "request", requestId, method: req.method, path: url.pathname, status, ms: now() - started });
    }
  });

  server.requestTimeout = 60_000;
  server.headersTimeout = 15_000;
  server.keepAliveTimeout = 5_000;
  return server;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const port = Number(process.env.PORT) || 8080;
  let app;
  try {
    app = createApp();
  } catch (error) {
    console.error(`Gagal memulai: ${error.message}`);
    process.exit(1);
  }
  app.listen(port, () => log({ level: "info", event: "listening", port }));
  const stop = () => app.close(() => process.exit(0));
  process.on("SIGTERM", stop);
  process.on("SIGINT", stop);
}

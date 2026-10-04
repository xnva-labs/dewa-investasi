// Model providers. `anthropic` calls the Messages API with the key from the server environment;
// `echo` is a deterministic offline provider for local development and tests (no network, no key).

import { HttpError } from "./validate.mjs";
import { chatSystemPrompt, VISION_SYSTEM, translateSystem } from "./prompts.mjs";

export function createProvider(env = process.env) {
  const name = (env.XNAI_PROVIDER || (env.ANTHROPIC_API_KEY ? "anthropic" : "")).toLowerCase();
  if (name === "echo") return new EchoProvider();
  if (name === "anthropic") {
    if (!env.ANTHROPIC_API_KEY) throw new Error("ANTHROPIC_API_KEY belum diatur.");
    return new AnthropicProvider({
      apiKey: env.ANTHROPIC_API_KEY,
      baseUrl: env.ANTHROPIC_BASE_URL || "https://api.anthropic.com",
      model: env.XNAI_MODEL || "claude-sonnet-5-5",
      maxTokens: Number(env.XNAI_MAX_TOKENS) || 700,
      timeoutMs: Number(env.XNAI_PROVIDER_TIMEOUT_MS) || 30_000,
    });
  }
  throw new Error("Atur XNAI_PROVIDER=anthropic (dengan ANTHROPIC_API_KEY) atau XNAI_PROVIDER=echo untuk pengembangan lokal.");
}

export class EchoProvider {
  name = "echo";
  async chat(req) {
    return `Echo XNAI (${req.thinkMode}/${req.ageBand}): ${req.message}`;
  }
  async vision() {
    return "Echo XNAI: gambar JPEG diterima (provider uji, tanpa analisis nyata).";
  }
  async translate({ text, targetLanguage }) {
    return `[${targetLanguage}] ${text}`;
  }
}

export class AnthropicProvider {
  name = "anthropic";
  constructor({ apiKey, baseUrl, model, maxTokens, timeoutMs }) {
    this.apiKey = apiKey;
    this.baseUrl = baseUrl.replace(/\/+$/, "");
    this.model = model;
    this.maxTokens = maxTokens;
    this.timeoutMs = timeoutMs;
  }

  async #call({ system, messages, maxTokens }) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), this.timeoutMs);
    let res;
    try {
      res = await fetch(`${this.baseUrl}/v1/messages`, {
        method: "POST",
        signal: controller.signal,
        headers: {
          "content-type": "application/json",
          "x-api-key": this.apiKey,
          "anthropic-version": "2023-06-01",
        },
        body: JSON.stringify({ model: this.model, max_tokens: maxTokens ?? this.maxTokens, system, messages }),
      });
    } catch (error) {
      if (error?.name === "AbortError") throw new HttpError(504, "Model AI terlalu lama merespons. Coba lagi.");
      throw new HttpError(502, "Tidak dapat menghubungi layanan model AI.");
    } finally {
      clearTimeout(timer);
    }
    if (res.status === 429) throw new HttpError(503, "Layanan AI sedang padat. Coba lagi sebentar.");
    if (res.status === 401 || res.status === 403) {
      // A server-side credential problem must be fixed by the operator; do not leak details to the app.
      console.error(JSON.stringify({ level: "error", event: "provider_auth_failed", status: res.status }));
      throw new HttpError(502, "Layanan AI sedang tidak tersedia.");
    }
    if (!res.ok) {
      console.error(JSON.stringify({ level: "error", event: "provider_error", status: res.status }));
      throw new HttpError(502, "Layanan AI gagal memproses permintaan.");
    }
    let data;
    try {
      data = await res.json();
    } catch {
      throw new HttpError(502, "Respons layanan AI tidak valid.");
    }
    const text = (Array.isArray(data?.content) ? data.content : [])
      .filter((block) => block?.type === "text" && typeof block.text === "string")
      .map((block) => block.text)
      .join("")
      .trim();
    if (!text) throw new HttpError(502, "Layanan AI mengembalikan jawaban kosong.");
    return text;
  }

  chat(req) {
    // Anthropic requires alternating roles starting with "user"; merge adjacent same-role turns and drop a leading assistant turn.
    const turns = [];
    for (const turn of [...req.history, { role: "user", text: req.message }]) {
      const last = turns[turns.length - 1];
      if (last && last.role === turn.role) last.content += `\n${turn.text}`;
      else turns.push({ role: turn.role, content: turn.text });
    }
    while (turns.length > 1 && turns[0].role !== "user") turns.shift();
    return this.#call({ system: chatSystemPrompt(req), messages: turns });
  }

  vision({ imageBase64, mimeType }) {
    return this.#call({
      system: VISION_SYSTEM,
      maxTokens: 400,
      messages: [
        {
          role: "user",
          content: [
            { type: "image", source: { type: "base64", media_type: mimeType, data: imageBase64 } },
            { type: "text", text: "Apa yang ada di gambar ini?" },
          ],
        },
      ],
    });
  }

  translate({ text, targetLanguage }) {
    return this.#call({
      system: translateSystem(targetLanguage),
      maxTokens: Math.min(2000, Math.max(300, Math.ceil(text.length * 1.5))),
      messages: [{ role: "user", content: text }],
    });
  }
}

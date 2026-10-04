// Request validation. Limits mirror the Android client (XnaiRepository, TranslationRepository, VisionRepository)
// so a request the app can legally send is accepted, and everything else is rejected before any model cost.

export class HttpError extends Error {
  constructor(status, message, headers = {}) {
    super(message);
    this.status = status;
    this.headers = headers;
  }
}

export const LIMITS = Object.freeze({
  message: 4000,
  history: 20,
  historyText: 4000,
  context: 6000,
  companion: 5000,
  reasoning: 4000,
  thinkMode: 40,
  translateText: 10000,
  targetLanguage: 20,
  imageBytes: 2_000_000,
});

const ALLOWED_THINK = new Set(["RELAX", "HIGH", "SUPERAUTOMATION"]);
const ALLOWED_IMAGE = new Set(["image/jpeg"]);

function str(value, name, max, { required = false } = {}) {
  if (value === undefined || value === null) {
    if (required) throw new HttpError(400, `Field '${name}' wajib diisi.`);
    return "";
  }
  if (typeof value !== "string") throw new HttpError(400, `Field '${name}' harus berupa teks.`);
  if (value.length > max) throw new HttpError(400, `Field '${name}' terlalu panjang (maks ${max}).`);
  return value;
}

export function parseChat(body) {
  if (!body || typeof body !== "object" || Array.isArray(body)) throw new HttpError(400, "Body harus berupa objek JSON.");
  const message = str(body.message, "message", LIMITS.message, { required: true }).trim();
  if (!message) throw new HttpError(400, "Pesan kosong.");
  const history = [];
  if (body.history !== undefined) {
    if (!Array.isArray(body.history)) throw new HttpError(400, "Field 'history' harus berupa array.");
    if (body.history.length > LIMITS.history) throw new HttpError(400, `Field 'history' maksimal ${LIMITS.history} item.`);
    for (const turn of body.history) {
      if (!turn || typeof turn !== "object") throw new HttpError(400, "Item history tidak valid.");
      const role = turn.role === "assistant" ? "assistant" : "user";
      const text = str(turn.text, "history.text", LIMITS.historyText).trim();
      if (text) history.push({ role, text });
    }
  }
  const thinkRaw = str(body.thinkMode, "thinkMode", LIMITS.thinkMode).toUpperCase();
  const hour = Number.isInteger(body.localHour) && body.localHour >= 0 && body.localHour <= 23 ? body.localHour : null;
  return {
    localHour: hour,
    message,
    history,
    thinkMode: ALLOWED_THINK.has(thinkRaw) ? thinkRaw : "RELAX",
    // Client-supplied personalization is untrusted *data*; it never becomes system instructions.
    context: str(body.context, "context", LIMITS.context),
    companion: str(body.companion, "companion", LIMITS.companion),
    reasoning: str(body.reasoning, "reasoning", LIMITS.reasoning),
  };
}

export function parseTranslate(body) {
  if (!body || typeof body !== "object" || Array.isArray(body)) throw new HttpError(400, "Body harus berupa objek JSON.");
  const text = str(body.text, "text", LIMITS.translateText, { required: true }).trim();
  const targetLanguage = str(body.targetLanguage, "targetLanguage", LIMITS.targetLanguage, { required: true }).trim();
  if (!text) throw new HttpError(400, "Teks kosong.");
  if (!targetLanguage) throw new HttpError(400, "Bahasa tujuan kosong.");
  if (!/^[\p{L}\p{M}\s'().,-]{2,20}$/u.test(targetLanguage)) {
    throw new HttpError(400, "Bahasa tujuan tidak valid.");
  }
  return { text, targetLanguage };
}

export function parseVision(body) {
  if (!body || typeof body !== "object" || Array.isArray(body)) throw new HttpError(400, "Body harus berupa objek JSON.");
  const mimeType = str(body.mimeType, "mimeType", 40, { required: true }).toLowerCase();
  if (!ALLOWED_IMAGE.has(mimeType)) throw new HttpError(415, "Hanya image/jpeg yang didukung.");
  const b64 = str(body.imageBase64, "imageBase64", Math.ceil(LIMITS.imageBytes * 1.4), { required: true });
  if (!/^[A-Za-z0-9+/]+={0,2}$/.test(b64)) throw new HttpError(400, "imageBase64 bukan base64 yang valid.");
  const bytes = Buffer.from(b64, "base64");
  if (bytes.length === 0) throw new HttpError(400, "Gambar kosong.");
  if (bytes.length > LIMITS.imageBytes) throw new HttpError(413, "Gambar terlalu besar.");
  // JPEG files start with FF D8 FF. Reject anything else before paying for a model call.
  if (!(bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff)) throw new HttpError(415, "Data bukan JPEG yang valid.");
  return { imageBase64: b64, mimeType };
}

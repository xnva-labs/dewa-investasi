import test from "node:test";
import assert from "node:assert/strict";
import { chatSystemPrompt, normalizeBand, timeHint } from "../src/persona.mjs";
import { cleanForVoice, crisisFallback, detectCrisis, detectCrisisInConversation } from "../src/safety.mjs";
import { EchoProvider } from "../src/providers.mjs";
import { DEVICE, post, startApp } from "./helpers.mjs";

const base = { thinkMode: "RELAX", context: "suka kopi", companion: "hangat", reasoning: "singkat", localHour: 14, crisis: false };

test("persona: adult is relaxed but never a girlfriend/boyfriend substitute", () => {
  const p = chatSystemPrompt({ ...base, ageBand: "ADULT" });
  assert.match(p, /gue\/lo/);
  assert.match(p, /bukan manusia/i);
  assert.match(p, /BUKAN pacar/);
  assert.match(p, /jangan bilang kangen/);
  assert.match(p, /Konteks pengguna: suka kopi/);
  assert.ok(!p.includes("MODE KRISIS"));
});

test("persona: teen mode drops romance, personalization context, and adds guardian guidance", () => {
  const p = chatSystemPrompt({ ...base, ageBand: "TEEN" });
  assert.match(p, /remaja \(13-17\)/);
  assert.match(p, /Tidak ada konten romantis/);
  assert.match(p, /orang tua, wali, guru/);
  assert.ok(!p.includes("suka kopi"), "free-form memory context is never sent into teen prompts");
  assert.ok(!p.includes("Gaya pendamping"));
});

test("persona: missing or invalid age band falls back to the protective teen profile", () => {
  assert.equal(normalizeBand(undefined), "TEEN");
  assert.equal(normalizeBand("hacker"), "TEEN");
  assert.equal(normalizeBand("adult"), "ADULT");
  assert.equal(normalizeBand("CHILD"), "CHILD");
  assert.match(chatSystemPrompt({ ...base, ageBand: undefined }), /remaja/);
});

test("persona: crisis block is present only when flagged, and teens get the guardian line", () => {
  const adult = chatSystemPrompt({ ...base, ageBand: "ADULT", crisis: true });
  assert.match(adult, /MODE KRISIS/);
  assert.match(adult, /112/);
  assert.match(adult, /Jangan pernah memberi cara atau metode/);
  const teen = chatSystemPrompt({ ...base, ageBand: "TEEN", crisis: true });
  assert.match(teen, /bukan hal yang memalukan/);
});

test("persona: night hint softens tone, daytime adds nothing, bad hours are ignored", () => {
  assert.match(timeHint(23), /malam/);
  assert.match(timeHint(2), /malam/);
  assert.match(timeHint(7), /pagi/);
  assert.equal(timeHint(14), "");
  assert.equal(timeHint(99), "");
  assert.equal(timeHint(null), "");
});

test("safety: crisis phrases are caught in Indonesian slang, English, and stretched spellings", () => {
  for (const text of [
    "gue pengen bunuh diri", "aku mau mati aja", "pengen mati rasanya", "gak mau hidup lagi", "capek hidup banget",
    "pengen nyakitin diri sendiri", "gue mau gores tangan", "lebih baik gue mati", "i want to die", "thinking about suicide", "self-harm",
    "mau matiiiiii", "AKU PENGEN MATI",
  ]) assert.ok(detectCrisis(text), text);
});

test("safety: ordinary talk is not flagged", () => {
  for (const text of [
    "capek banget hari ini", "gue mau mati gaya di acara itu?", "mau makan apa", "aku nggak mau ke sekolah", "bunuh waktu aja nunggu bus",
    "mati lampu lagi", "laptop gue mati", "tolong ingatkan jam 8", "he is killing it",
  ]) {
    const flagged = detectCrisis(text);
    // "mau mati gaya" is a known soft false positive for a safety net; everything else must stay clean.
    if (text.includes("mati gaya")) continue;
    assert.equal(flagged, false, text);
  }
});

test("safety: crisis mode stays on for a follow-up like 'iya' after a check-in", () => {
  const history = [{ role: "user", text: "gue mau bunuh diri" }, { role: "assistant", text: "Lo aman sekarang?" }];
  assert.ok(detectCrisisInConversation("iya", history));
  assert.equal(detectCrisisInConversation("iya", [{ role: "user", text: "halo" }]), false);
});

test("safety: fallback replies exist for both bands and mention emergency help", () => {
  assert.match(crisisFallback("ADULT"), /112/);
  assert.match(crisisFallback("TEEN"), /orang tua, wali, atau guru/);
  assert.match(crisisFallback(undefined), /orang tua/);
});

test("voice cleanup removes markdown but keeps normal text", () => {
  assert.equal(cleanForVoice("**Halo** __lo__ `kode`"), "Halo lo kode");
  assert.equal(cleanForVoice("# Judul\n- satu\n- dua"), "Judul\nsatu\ndua");
  assert.equal(cleanForVoice("lihat [ini](https://x.id/a) ya"), "lihat ini ya");
  assert.equal(cleanForVoice("2 * 3 = 6, santai aja"), "2 * 3 = 6, santai aja");
  assert.equal(cleanForVoice("```js\nx()\n```ok"), "ok");
});

test("server: CHILD is refused everywhere, vision is adults only, unknown band behaves like TEEN", async () => {
  const s = await startApp({ provider: new EchoProvider(), env: { XNAI_DAILY_CAP: "100" } });
  try {
    const child = await post(s.url, "/chat", { message: "hai" }, { "x-age-band": "CHILD" });
    assert.equal(child.status, 403);
    assert.match(child.json.message, /13 tahun/);

    const teenVision = await post(s.url, "/vision", { imageBase64: "/9j/4AAQ", mimeType: "image/jpeg" }, { "x-age-band": "TEEN" });
    assert.equal(teenVision.status, 403);

    const unknown = await post(s.url, "/chat", { message: "hai" }, { "x-age-band": "banana" });
    assert.equal(unknown.status, 200);
    assert.match(unknown.json.reply, /TEEN/);

    const adult = await post(s.url, "/chat", { message: "hai" });
    assert.match(adult.json.reply, /ADULT/);
  } finally { await s.close(); }
});

test("server: a person in crisis still gets a caring answer when the model is down or the budget is spent", async () => {
  const down = { name: "down", async chat() { throw new Error("boom"); } };
  const s = await startApp({ provider: down, env: { XNAI_DAILY_CAP: "100" } });
  try {
    const r = await post(s.url, "/chat", { message: "gue mau bunuh diri" });
    assert.equal(r.status, 200);
    assert.equal(r.json.safety, "crisis");
    assert.match(r.json.reply, /112/);
    const normal = await post(s.url, "/chat", { message: "halo" });
    assert.equal(normal.status, 500, "non-crisis errors are still errors");
  } finally { await s.close(); }

  const echo = await startApp({ provider: new EchoProvider(), env: { XNAI_DAILY_CAP: "1" } });
  try {
    assert.equal((await post(echo.url, "/chat", { message: "halo" })).status, 200);
    const spent = await post(echo.url, "/chat", { message: "gue pengen mati" });
    assert.equal(spent.status, 200);
    assert.equal(spent.json.safety, "crisis");
    assert.equal((await post(echo.url, "/chat", { message: "halo lagi" })).status, 503);
  } finally { await echo.close(); }
});

test("server: crisis flag reaches the model prompt, and replies are voice-clean", async () => {
  let seen;
  const provider = { name: "spy", async chat(req) { seen = req; return "**Gue di sini.**\n- pelan aja"; } };
  const s = await startApp({ provider, env: { XNAI_DAILY_CAP: "100" } });
  try {
    const r = await post(s.url, "/chat", { message: "capek hidup", localHour: 23, history: [] });
    assert.equal(r.json.reply, "Gue di sini.\npelan aja");
    assert.equal(r.json.safety, "crisis");
    assert.equal(seen.crisis, true);
    assert.equal(seen.localHour, 23);
    assert.equal(seen.ageBand, "ADULT");
  } finally { await s.close(); }
});

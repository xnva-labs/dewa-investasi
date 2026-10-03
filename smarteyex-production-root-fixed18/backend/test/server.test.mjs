import test from "node:test";
import assert from "node:assert/strict";
import { EchoProvider } from "../src/providers.mjs";
import { DEVICE, JPEG_B64, post, startApp } from "./helpers.mjs";

const env = (extra = {}) => ({ XNAI_DAILY_CAP: "1000", ...extra });

test("health and routing", async () => {
  const s = await startApp({ provider: new EchoProvider(), env: env() });
  try {
    const health = await fetch(`${s.url}/health`);
    assert.equal(health.status, 200);
    assert.deepEqual(await health.json(), { status: "ok", provider: "echo" });
    assert.equal((await fetch(`${s.url}/nope`)).status, 404);
    const get = await fetch(`${s.url}/chat`);
    assert.equal(get.status, 405);
    assert.equal(get.headers.get("allow"), "POST");
  } finally { await s.close(); }
});

test("contract: /chat, /translate, /vision return the fields the Android app reads", async () => {
  const s = await startApp({ provider: new EchoProvider(), env: env() });
  try {
    const chat = await post(s.url, "/chat", { requestId: "r1", message: "halo", thinkMode: "HIGH", history: [], platform: "android" });
    assert.equal(chat.status, 200);
    assert.match(chat.json.reply, /halo/);

    const tr = await post(s.url, "/translate", { text: "selamat pagi", targetLanguage: "English" });
    assert.equal(tr.status, 200);
    assert.ok(tr.json.translated.length > 0);

    const vis = await post(s.url, "/vision", { imageBase64: JPEG_B64, mimeType: "image/jpeg" });
    assert.equal(vis.status, 200);
    assert.ok(vis.json.description.length > 0);
    assert.equal(vis.headers.get("cache-control"), "no-store");
  } finally { await s.close(); }
});

test("errors use {message} so NetworkClient.requireSuccess can show them", async () => {
  const s = await startApp({ provider: new EchoProvider(), env: env() });
  try {
    const noDevice = await fetch(`${s.url}/chat`, { method: "POST", headers: { "content-type": "application/json" }, body: "{}" });
    assert.equal(noDevice.status, 400);
    assert.ok((await noDevice.json()).message);

    const badType = await fetch(`${s.url}/chat`, { method: "POST", headers: { "content-type": "text/plain", "x-device-id": DEVICE }, body: "hi" });
    assert.equal(badType.status, 415);

    const badJson = await post(s.url, "/chat", "{not json");
    assert.equal(badJson.status, 400);
    assert.ok(badJson.json.message);

    const empty = await post(s.url, "/chat", { message: "   " });
    assert.equal(empty.status, 400);

    const tooBig = await post(s.url, "/chat", { message: "x".repeat(70_000) });
    assert.equal(tooBig.status, 413);
  } finally { await s.close(); }
});

test("rate limit: per-device burst returns 429 with Retry-After", async () => {
  const s = await startApp({ provider: new EchoProvider(), env: env({ RATE_DEVICE_BURST: "3", RATE_DEVICE_PER_SEC: "0.01" }) });
  try {
    for (let i = 0; i < 3; i++) assert.equal((await post(s.url, "/chat", { message: `m${i}` })).status, 200);
    const limited = await post(s.url, "/chat", { message: "again" });
    assert.equal(limited.status, 429);
    assert.ok(Number(limited.headers.get("retry-after")) >= 1);
    const other = await post(s.url, "/chat", { message: "x" }, { "x-device-id": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee" });
    assert.equal(other.status, 200, "another device is not affected");
  } finally { await s.close(); }
});

test("idempotency: an app retry with the same key reaches the model once", async () => {
  let calls = 0;
  const provider = { name: "counting", async chat() { calls++; return "jawaban"; } };
  const s = await startApp({ provider, env: env() });
  try {
    const headers = { "idempotency-key": "11111111-aaaa-bbbb-cccc-222222222222" };
    const a = await post(s.url, "/chat", { message: "tes" }, headers);
    const b = await post(s.url, "/chat", { message: "tes" }, headers);
    assert.deepEqual([a.status, b.status, a.json.reply, b.json.reply], [200, 200, "jawaban", "jawaban"]);
    assert.equal(calls, 1);
    await post(s.url, "/chat", { message: "tes" }, { "idempotency-key": "99999999-aaaa-bbbb-cccc-222222222222" });
    assert.equal(calls, 2);
  } finally { await s.close(); }
});

test("daily budget: stops model calls, and a failing provider does not burn budget", async () => {
  let fail = true;
  const provider = { name: "flaky", async chat() { if (fail) throw new Error("down"); return "ok"; } };
  const s = await startApp({ provider, env: env({ XNAI_DAILY_CAP: "2" }) });
  try {
    assert.equal((await post(s.url, "/chat", { message: "a" })).status, 500);
    assert.equal((await post(s.url, "/chat", { message: "b" })).status, 500);
    fail = false;
    assert.equal((await post(s.url, "/chat", { message: "c" })).status, 200, "failed calls were refunded");
    assert.equal((await post(s.url, "/chat", { message: "d" })).status, 200);
    const over = await post(s.url, "/chat", { message: "e" });
    assert.equal(over.status, 503);
    assert.match(over.json.message, /Kuota/);
  } finally { await s.close(); }
});

test("logs never contain message content", async () => {
  const lines = [];
  const original = console.log;
  const s = await startApp({ provider: new EchoProvider(), env: env() });
  console.log = (line) => lines.push(String(line));
  try {
    await post(s.url, "/chat", { message: "RAHASIA-12345 nomor kartu" });
    assert.ok(lines.length > 0, "requests are logged");
    assert.ok(lines.every((l) => !l.includes("RAHASIA")), "message text must not be logged");
  } finally { console.log = original; await s.close(); }
});

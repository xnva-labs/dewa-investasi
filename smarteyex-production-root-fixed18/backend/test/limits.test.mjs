import test from "node:test";
import assert from "node:assert/strict";
import { DailyBudget, IdempotencyCache, TokenBucket } from "../src/limits.mjs";

test("token bucket: burst, then refill over time", () => {
  let t = 0;
  const b = new TokenBucket({ capacity: 3, refillPerSecond: 1, now: () => t });
  assert.ok(b.take("a").allowed && b.take("a").allowed && b.take("a").allowed);
  const denied = b.take("a");
  assert.equal(denied.allowed, false);
  assert.ok(denied.retryAfterSeconds >= 1);
  assert.ok(b.take("other").allowed, "keys are independent");
  t += 1000;
  assert.ok(b.take("a").allowed, "one token refilled after 1s");
  assert.equal(b.take("a").allowed, false);
});

test("token bucket: bounded memory", () => {
  const b = new TokenBucket({ capacity: 1, refillPerSecond: 1, maxKeys: 5 });
  for (let i = 0; i < 50; i++) b.take(`k${i}`);
  assert.ok(b.buckets.size <= 5);
});

test("daily budget: caps, refunds, rolls over at UTC midnight, 0 disables", () => {
  let t = Date.parse("2026-10-03T23:59:00Z");
  const d = new DailyBudget({ cap: 2, now: () => t });
  assert.ok(d.tryUse() && d.tryUse());
  assert.equal(d.tryUse(), false);
  d.refund();
  assert.ok(d.tryUse());
  t = Date.parse("2026-10-04T00:00:01Z");
  assert.ok(d.tryUse(), "new day resets the counter");
  const unlimited = new DailyBudget({ cap: 0 });
  for (let i = 0; i < 100; i++) assert.ok(unlimited.tryUse());
});

test("idempotency: duplicates share one execution; failures are retried", async () => {
  const cache = new IdempotencyCache();
  let calls = 0;
  const slow = async () => { calls++; await new Promise((r) => setTimeout(r, 20)); return "ok"; };
  const [a, b] = await Promise.all([cache.run("k", slow), cache.run("k", slow)]);
  assert.equal(a, "ok"); assert.equal(b, "ok"); assert.equal(calls, 1);
  assert.equal(await cache.run("k", slow), "ok"); assert.equal(calls, 1);

  let attempts = 0;
  const flaky = async () => { attempts++; if (attempts === 1) throw new Error("boom"); return "fine"; };
  await assert.rejects(cache.run("f", flaky));
  assert.equal(await cache.run("f", flaky), "fine");
  assert.equal(attempts, 2);
  assert.equal(await cache.run(null, async () => "no-key"), "no-key");
});

test("idempotency: entries expire", async () => {
  let t = 0;
  const cache = new IdempotencyCache({ ttlMs: 1000, now: () => t });
  let n = 0;
  await cache.run("k", async () => ++n);
  t = 2000;
  await cache.run("k", async () => ++n);
  assert.equal(n, 2);
});

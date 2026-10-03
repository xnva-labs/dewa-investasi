// In-memory rate limiting, idempotency cache, and a daily request budget.
// This is per-process state: with several instances either run one instance or move these to Redis.

export class TokenBucket {
  constructor({ capacity, refillPerSecond, maxKeys = 10_000, now = () => Date.now() }) {
    this.capacity = capacity;
    this.refillPerSecond = refillPerSecond;
    this.maxKeys = maxKeys;
    this.now = now;
    this.buckets = new Map();
  }

  /** Returns { allowed, retryAfterSeconds }. */
  take(key) {
    const t = this.now();
    let b = this.buckets.get(key);
    if (!b) {
      if (this.buckets.size >= this.maxKeys) this.buckets.delete(this.buckets.keys().next().value);
      b = { tokens: this.capacity, at: t };
    } else {
      b.tokens = Math.min(this.capacity, b.tokens + ((t - b.at) / 1000) * this.refillPerSecond);
      b.at = t;
      this.buckets.delete(key);
    }
    this.buckets.set(key, b);
    if (b.tokens >= 1) {
      b.tokens -= 1;
      return { allowed: true, retryAfterSeconds: 0 };
    }
    return { allowed: false, retryAfterSeconds: Math.max(1, Math.ceil((1 - b.tokens) / this.refillPerSecond)) };
  }
}

export class DailyBudget {
  constructor({ cap, now = () => Date.now() }) {
    this.cap = cap;
    this.now = now;
    this.day = this.#dayKey();
    this.used = 0;
  }

  #dayKey() {
    return new Date(this.now()).toISOString().slice(0, 10);
  }

  /** Reserve one unit; false when today's cap is exhausted. cap <= 0 disables the limit. */
  tryUse() {
    const today = this.#dayKey();
    if (today !== this.day) {
      this.day = today;
      this.used = 0;
    }
    if (this.cap > 0 && this.used >= this.cap) return false;
    this.used += 1;
    return true;
  }

  /** Give back a unit when the request failed before reaching the model. */
  refund() {
    if (this.used > 0) this.used -= 1;
  }
}

/**
 * Remembers completed responses by Idempotency-Key so the app's automatic retries (it retries 5xx/timeouts)
 * never pay for or apply the same request twice. Concurrent duplicates share one in-flight promise.
 */
export class IdempotencyCache {
  constructor({ ttlMs = 5 * 60_000, maxEntries = 500, now = () => Date.now() } = {}) {
    this.ttlMs = ttlMs;
    this.maxEntries = maxEntries;
    this.now = now;
    this.entries = new Map();
  }

  async run(key, producer) {
    if (!key) return producer();
    const t = this.now();
    const hit = this.entries.get(key);
    if (hit && t - hit.at <= this.ttlMs) return hit.promise;
    const promise = producer();
    this.entries.set(key, { at: t, promise });
    while (this.entries.size > this.maxEntries) this.entries.delete(this.entries.keys().next().value);
    try {
      return await promise;
    } catch (error) {
      // Failures are not cached: the retry must be allowed to try again.
      if (this.entries.get(key)?.promise === promise) this.entries.delete(key);
      throw error;
    }
  }
}

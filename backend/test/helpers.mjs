import http from "node:http";
import { createApp } from "../src/server.mjs";

export const DEVICE = "11111111-2222-3333-4444-555555555555";

export function listen(server) {
  return new Promise((resolve) => server.listen(0, "127.0.0.1", () => resolve(server.address().port)));
}

export async function startApp(options) {
  const quiet = console.log;
  console.log = () => {}; // silence request logs inside tests
  const app = createApp(options);
  const port = await listen(app);
  return {
    port,
    url: `http://127.0.0.1:${port}`,
    async close() {
      console.log = quiet;
      await new Promise((r) => app.close(r));
    },
  };
}

export async function post(base, path, body, headers = {}) {
  const res = await fetch(base + path, {
    method: "POST",
    headers: { "content-type": "application/json", "x-device-id": DEVICE, ...headers },
    body: typeof body === "string" ? body : JSON.stringify(body),
  });
  let json = null;
  try {
    json = await res.json();
  } catch {}
  return { status: res.status, json, headers: res.headers };
}

/** A tiny stand-in for api.anthropic.com that records what it receives. */
export async function fakeAnthropic(handler) {
  const seen = [];
  const server = http.createServer(async (req, res) => {
    const chunks = [];
    for await (const c of req) chunks.push(c);
    const body = JSON.parse(Buffer.concat(chunks).toString("utf8") || "{}");
    seen.push({ method: req.method, url: req.url, headers: req.headers, body });
    const out = await handler(body, seen.length);
    res.writeHead(out.status ?? 200, { "content-type": "application/json" });
    res.end(JSON.stringify(out.json ?? {}));
  });
  const port = await listen(server);
  return { seen, url: `http://127.0.0.1:${port}`, close: () => new Promise((r) => server.close(r)) };
}

export const JPEG_B64 = Buffer.from([0xff, 0xd8, 0xff, 0xe0, 0, 16, 0x4a, 0x46, 0x49, 0x46, 0, 1, 1, 0, 0, 1, 0, 1, 0, 0, 0xff, 0xd9]).toString("base64");

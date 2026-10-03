# XNAI backend

API yang dipanggil aplikasi SmartEyeX. Tanpa dependensi npm (Node >= 20).

| Endpoint | Body dari app | Respons |
|---|---|---|
| `POST /chat` | `message`, `history[]`, `thinkMode`, `context`, `companion`, `reasoning` | `{ "reply": "..." }` |
| `POST /translate` | `text`, `targetLanguage` | `{ "translated": "..." }` |
| `POST /vision` | `imageBase64`, `mimeType` (`image/jpeg`) | `{ "description": "..." }` |
| `GET /health` | - | `{ "status": "ok" }` |

Kesalahan selalu `{ "message": "..." }` (dibaca `NetworkClient.requireSuccess`). Header wajib: `X-Device-Id` (ID acak per instalasi).
`Idempotency-Key` dipakai agar retry otomatis aplikasi tidak memanggil model dua kali.

## Yang sudah diuji (`npm test`, 26 tes, lokal tanpa internet)
validasi payload sesuai batas aplikasi, rate limit per perangkat/IP, anggaran harian, idempotensi, log tanpa isi pesan,
dan bentuk permintaan/respons Messages API terhadap server tiruan.

## Yang BELUM terbukti
- Panggilan sungguhan ke API Anthropic (tidak ada internet/kunci saat dibuat). Jalankan sekali dengan kunci asli dan cek `/chat`.
- Nama model default `claude-sonnet-5-5` dan batas biaya perlu kamu sesuaikan dengan akun/anggaranmu.
- Rate limit dan anggaran ada di memori satu proses. Jalankan 1 instance, atau pindahkan ke Redis bila lebih.
- `X-Device-Id` hanya pembatas penyalahgunaan, **bukan autentikasi**: siapa pun bisa membuat ID baru. Perlindungan sebenarnya
  (Play Integrity / App Check) dikerjakan di tahap Play Store.

## Menjalankan
```
# lokal tanpa kunci (balasan uji)
XNAI_PROVIDER=echo node src/server.mjs
# produksi
ANTHROPIC_API_KEY=... XNAI_PROVIDER=anthropic TRUST_PROXY=1 node src/server.mjs
```
Deploy: `Dockerfile` siap pakai di Render / Fly.io / Railway / Cloud Run (wajib HTTPS dari platform). Simpan `ANTHROPIC_API_KEY`
di secret manager platform, bukan di repo.

## Mengaktifkan di aplikasi
Setelah deploy dan `GET <url>/health` menjawab ok, isi `backendBaseUrl` di `xnai-config.json` (branch `main`, repo harus publik
agar `raw.githubusercontent.com` bisa dibaca) dengan URL HTTPS tersebut. Tidak perlu build ulang APK.

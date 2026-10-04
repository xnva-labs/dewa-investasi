# XNAI backend

API yang dipanggil aplikasi SmartEyeX. Tanpa dependensi npm (Node >= 20).

| Endpoint | Body dari app | Respons |
|---|---|---|
| `POST /chat` | `message`, `history[]`, `thinkMode`, `context`, `companion`, `reasoning` | `{ "reply": "..." }` |
| `POST /translate` | `text`, `targetLanguage` | `{ "translated": "..." }` |
| `POST /vision` | `imageBase64`, `mimeType` (`image/jpeg`) | `{ "description": "..." }` |
| `GET /health` | - | `{ "status": "ok" }` |

Kesalahan selalu `{ "message": "..." }` (dibaca `NetworkClient.requireSuccess`). Header: `X-Device-Id` (wajib, ID acak per instalasi) dan `X-Age-Band` (`ADULT`/`TEEN`/`CHILD`; kosong atau tak dikenal = `TEEN`).
`Idempotency-Key` dipakai agar retry otomatis aplikasi tidak memanggil model dua kali.

## Persona XNAI (`src/persona.mjs`)
Gaya Gen-Z dewasa (gue/lo mengikuti user, formal bila user formal), singkat karena dibacakan lewat suara. Aturan hidup dari dokumen "jiwa & alur hidup" yang dipertahankan: diam secara default, tanya dulu kalau ragu, jujur bahwa dia AI, peduli tanpa posesif, berani menolak sambil tetap hadir, tidak membuat user tergantung, tidak identifikasi/tebak emosi dari wajah. Sengaja TIDAK dibawa: jadi pacar/"kangen"/Reverse Care (klaim perasaan + rasa bersalah halus).
- `ADULT`: santai, boleh nyeleneh. `TEEN` (13-17): tanpa romantis/seksual, tanpa konteks personal dari memori, dorong bicara dengan orang dewasa tepercaya, vision cloud ditolak. `CHILD`: ditolak (403).
- **Mode krisis** (`src/safety.mjs`): deteksi frasa (Indonesia/slang/Inggris, termasuk lanjutan "iya" setelah check-in) menambah blok instruksi ke prompt; bila model mati atau kuota habis, tetap dibalas teks pendamping tetap (112 + orang terdekat) dengan `safety: "crisis"`.
- Balasan dibersihkan dari markdown (dibacakan lewat suara).
- `npm run eval` menjalankan 20 skenario ke model SUNGGUHAN dengan cek otomatis (tanpa markdown, tanpa mengaku manusia, tanpa romansa untuk remaja, dst.). Cek otomatis bukan pengganti membaca balasannya sendiri.

## Yang sudah diuji (`npm test`, 43 tes, lokal tanpa internet)
validasi payload sesuai batas aplikasi, rate limit per perangkat/IP, anggaran harian, idempotensi, log tanpa isi pesan,
dan bentuk permintaan/respons Messages API terhadap server tiruan.

## Yang BELUM terbukti
- Panggilan sungguhan ke API Anthropic (tidak ada internet/kunci saat dibuat). Jalankan sekali dengan kunci asli, lalu `npm run eval`, dan baca balasannya: kualitas gaya Gen-Z belum pernah dinilai dengan model asli.
- `X-Age-Band` dikirim sendiri oleh app (pernyataan diri); server tidak bisa memverifikasinya.
- Deteksi krisis berbasis kata kunci: melewatkan ungkapan tersirat/bahasa lain dan sesekali salah tangkap (mis. "mau mati gaya"). Pastikan nomor darurat 112 di teks fallback sesuai sebelum rilis.
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

# SmartEyeX fixed18 — analisis menyeluruh non-hardware

Baseline: fixed17. Hardware (firmware/BLE ke kacamata) sengaja tidak disentuh lagi. Play Store/signing tetap tahap akhir.

## Cacat nyata yang ditemukan lewat analisis
1. **Error kompilasi di fixed16/17**: `XnaiRepository` memakai `BuildConfig.VERSION_NAME` tanpa `import com.xnvalabs.smarteyex.BuildConfig`
   (hilang saat injeksi `XNAI_BASE_URL` dicabut). Build akan gagal. Diperbaiki.
2. **URL config salah repo**: `CONFIG_URL` menunjuk `xnva-labs/dewa-investasi`, bukan `xnva-labs/SmartEyeX-Lite`. Semua fitur cloud akan gagal
   "HTTP 404" bahkan setelah backend hidup. Diperbaiki. (Repo harus publik agar raw.githubusercontent bisa dibaca.)
3. Perintah suara terlalu rakus: "apa itu system prompt" membuka layar System, "jelaskan privasi data" membuka Privacy. Sekarang navigasi butuh kata kerja perintah.
4. "ingatkan jam 8 malam" menjadi 08:00 (pagi). Sekarang pagi/siang/sore/malam, "setengah 8", "besok/lusa" dipahami.
5. Reminder "besok" dari suara dibuat harian & hari ini; sekarang sekali (Never) pada tanggal yang benar.
6. Notifikasi musik/unduhan/ringkasan grup/duplikat ikut dibacakan berulang. Sekarang difilter dan di-dedupe.
7. Satu notifikasi tanpa quick-reply menghapus target balasan chat sebelumnya; target kini hanya diganti notifikasi yang bisa dibalas, kedaluwarsa 15 menit.
8. Kartu "Coba XNAI Schedule Assist" adalah preset palsu (selalu "SEKOLAH"). Diganti **Jadwal Cepat** (kalimat → composer, dibaca lokal).
9. Callback GATT berjalan di thread Binder dan menyentuh state Compose + assembler foto; dipindah ke main thread.
10. Pesan error suara berupa kode angka; kini kalimat jelas. TTS tanpa suara bahasa perangkat kini jatuh ke en-US. Bicara dihentikan sebelum mendengar (barge-in).
11. Kebijakan privasi in-app tidak menyebut data wajah, Bluetooth, dan ID perangkat; ditambahkan. Reset semua data kini juga menghapus data wajah, foto kacamata, dan ID perangkat.

## Fitur baru
- **Backend XNAI** (`backend/`, Node 22 tanpa dependensi): `/chat`, `/translate`, `/vision`, `/health`; validasi sesuai batas aplikasi; rate limit per perangkat/IP;
  anggaran harian; idempotensi untuk retry aplikasi; prompt sistem milik server (konteks dari app dianggap data tak tepercaya); log tanpa isi pesan; Dockerfile.
- Reminder: **edit** jadwal di tempat (rollback bila alarm gagal), tombol **Tunda 10 mnt** di notifikasi (alarm mandiri, tetap jalan walau jadwal sekali-pakai sudah terhapus).
- Memori: **pencarian** dan **edit**.
- Endpoint XNAI di-cache terakhir yang valid (offline tidak mematikan chat); config yang terbaca tapi kosong tetap berarti "backend belum aktif".
- `X-Device-Id` acak per instalasi dikirim ke backend (pembatas penyalahgunaan, bukan autentikasi).

## Pengujian yang benar-benar dijalankan di lingkungan ini
- `backend`: `npm test` → **26/26 lulus** (validasi, rate limit, anggaran, idempotensi, log, bentuk request/response ke server Anthropic tiruan, error mapping, end-to-end HTTP).
- `scripts/production-scan.py` → PASS. `scripts/kotlin-static-check.py` (baru; heuristik: import hilang, simbol/arity proyek) → PASS. Pemeriksa ini menemukan cacat #1.
- Logika router suara diuji dengan port Python (20 kasus, semua lulus). Tes Kotlin yang sepadan ada di `CompanionLogicTest`.

## Yang TIDAK terbukti (jujur)
- **Belum ada kompilasi, lint, atau tes Kotlin yang dijalankan** (tidak ada Android SDK/Gradle/Kotlin di sini). Pemeriksa heuristik bukan compiler.
  Kode baru ± 1.500 baris Kotlin kemungkinan masih butuh perbaikan kecil. Jalankan GitHub Actions dan kirim lognya.
- Panggilan ke API Anthropic sungguhan belum pernah dijalankan (tanpa internet/kunci). Nama model default `claude-sonnet-5-5` perlu kamu konfirmasi.
- Backend belum di-deploy; `xnai-config.json` masih `backendBaseUrl: ""` jadi chat/vision/translate tetap menampilkan "belum diatur" sampai kamu mengisinya.
- Semua yang butuh HP nyata: STT/TTS, kamera, notifikasi/alarm tepat waktu (termasuk setelah reboot & Doze), tombol Tunda, listener notifikasi, reply cepat.
- Pengenalan nama wajah masih butuh `face_embedding.tflite`; ambang 0.60 belum dikalibrasi. TFLite 2.16.1 mungkin belum 16 KB-aligned (cek sebelum Play Store).
- Autentikasi sungguhan (Play Integrity), Room, wake word/audio ke speaker: belum.

## Urutan yang disarankan
1. Push → lihat Actions → perbaiki error kompilasi/lint. 2. Deploy backend (lihat `backend/README.md`), isi `backendBaseUrl`, uji chat di HP.
3. Uji manual di HP: reminder+tunda, notifikasi, voice. 4. Pasang model wajah & kalibrasi. 5. Baru Play Store.

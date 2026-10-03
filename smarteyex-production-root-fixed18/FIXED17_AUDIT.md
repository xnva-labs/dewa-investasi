# SmartEyeX fixed17 — fitur non-XNAI dibuat berfungsi

Baseline: `smarteyex-production-root-fixed16.zip`. Sesuai urutan kerja: **XNAI (backend) dan Play Store sengaja ditunda ke akhir**; `xnai-config.json`,
`XnaiRepository`, `/chat`, `/vision`, `/translate` dan alur rilis tidak diubah.

## Yang ditambahkan
1. **Tautan BLE ke kacamata (sebelumnya tidak ada kode sama sekali)** — `data/glasses/`
   - `GlassesProtocol.kt`: kontrak biner v1 + `FrameAssembler` (menolak urutan loncat, ukuran salah, CRC salah).
   - `GlassesRepository.kt`: scan (filter service UUID) → connect → MTU → discover → subscribe → status; reconnect otomatis maks. 3x;
     "Terhubung" hanya muncul setelah service lengkap dan kedua notifikasi aktif; foto hanya di memori; ditolak bila Camera OFF.
   - `DeviceScreen`: bagian KACAMATA (izin Bluetooth runtime, hubungkan/putuskan, ping, status baterai/kamera/mikrofon dari firmware,
     ambil foto, pratinjau, Kenali Wajah, Analisis Vision).
   - Manifest: `BLUETOOTH_SCAN` (neverForLocation), `BLUETOOTH_CONNECT`, `bluetooth_le` tidak wajib.
   - `firmware/`: sketch ESP32-C3 (NimBLE) + dokumen protokol.
2. **Face recognition on-device (sebelumnya hanya toggle)** — `data/face/`, `ui/screens/face/`
   - `FaceEngine`: ML Kit deteksi (klasifikasi emosi/landmark dimatikan) + embedding TFLite + `FaceMath` pencocokan konservatif
     (threshold + margin, "Tidak dikenal" bila ragu).
   - `FaceRepository`: template terenkripsi (Keystore), wajib toggle ON + centang persetujuan, hapus per orang / semua,
     state berubah hanya setelah tulis terverifikasi.
   - Mematikan toggle Face Recognition menghentikan pemrosesan **dan menghapus semua template**.
   - Layar Data Wajah (dari Device → Kelola Data Wajah).
3. Unit test baru: `GlassesProtocolTest`, `FaceMathTest` (logika murni).

## Batas verifikasi — baca ini
- Tidak ada Android SDK/Gradle di lingkungan penulisan: **belum ada compile, lint, atau test yang dijalankan**. Hanya `scripts/production-scan.py` (PASS).
  Jalankan GitHub Actions; kemungkinan perlu perbaikan kecil kompilasi pada kode baru.
- Dependensi baru belum pernah di-resolve: `com.google.mlkit:face-detection:16.1.7`, `org.tensorflow:tensorflow-lite:2.16.1`.
- **Pengenalan nama belum aktif sebelum `face_embedding.tflite` dipasang di `app/src/main/assets/`** (file model tidak ada di repo). Tanpa itu hanya deteksi,
  dan UI mengatakannya. Threshold 0.60/margin 0.05 belum dikalibrasi. Lihat `FACE_RECOGNITION.md`.
- Firmware belum dikompilasi atau diuji di board. ESP32-C3 tidak punya antarmuka kamera DVP: butuh modul kamera SPI/UART atau ESP32-S3.
  Audio mikrofon/speaker lewat BLE belum ada di protokol v1.
- Belum teruji di perangkat nyata: pairing/bonding, jarak, reconnect, throughput foto, konsumsi baterai.

## Masih ada di daftar (belum dikerjakan di revisi ini)
- Voice: wake word / audio routing ke bone-conduction (STT/TTS ponsel sudah ada).
- Kartu "COBA XNAI SCHEDULE ASSIST" di ReminderScreen masih preset demo (menyimpan jadwal SEKOLAH tetap) — ganti saat XNAI aktif.
- Penyimpanan memori/reminder masih JSON terenkripsi, bukan Room (belum ada migrasi/indeks pencarian).
- XNAI backend, privacy policy publik, signing & Play Store — tahap akhir.

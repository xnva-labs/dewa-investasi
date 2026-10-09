# Zahra v0.15.0 — App-first polish

Aplikasi pribadi (Android) untuk misi, daftar, reward, dan bukti kamera, ditambah **dunia 3D simulasi kehidupan** (Godot) yang tertanam di dalam APK yang sama.

> Status verifikasi: audit statis tersedia, tetapi paket ini belum berhasil di-build menjadi APK di lingkungan kerja ini karena Gradle/Android SDK tidak tersedia. Jalankan build dan unit test di Android Studio/CI sebelum instalasi final.

## Struktur repo

```
.
├── app/                         Modul Android (Kotlin, Jetpack Compose, Room, WorkManager, CameraX, ML Kit)
│   └── src/main/assets/         Game Three.js lokal di assets/game/index.html; halaman web alternatif di web/zahra-world.html
├── tests/godot/run_tests.gd     Smoke test Godot headless (parse semua skrip + jalankan dunia 3D beberapa hari)
├── tools/audit/                 Audit statis tanpa kompiler (python3 tools/audit/audit_all.py)
├── docs/                        Status, changelog, laporan verifikasi, gerbang rilis, desain World Brain
├── gradle/wrapper/              gradle-wrapper.properties (Gradle 9.4.1)
└── .github/workflows/           android.yml (unit test + APK debug), godot.yml (smoke test Godot)
```

## Cara build

Prasyarat: JDK 17+, Android SDK (platform 36, build-tools 36.0.0), Android Studio terbaru.

1. Buka folder ini di Android Studio, tunggu sync. (Studio memakai `gradle/wrapper/gradle-wrapper.properties` -> Gradle 9.4.1.)
2. Opsional untuk terminal: `gradle wrapper --gradle-version 9.4.1` sekali saja (membuat `gradlew` dan `gradle-wrapper.jar`), lalu commit.
3. `./gradlew testDebugUnitTest assembleDebug`
4. APK ada di `app/build/outputs/apk/debug/`.

Catatan versi: AGP 9.2 memakai Kotlin bawaan. **Jangan** menambahkan plugin `org.jetbrains.kotlin.android`; versi Kotlin Gradle Plugin dipaksa 2.4.20 lewat `buildscript` di `build.gradle.kts` agar cocok dengan plugin Compose.

## Pemeriksaan

| Perintah | Fungsi |
|---|---|
| `python3 tools/audit/audit_all.py` | Audit statis (DAO tak terdaftar, urutan argumen Compose, `:=` GDScript dari Variant, dsb.) |
| `gradle testDebugUnitTest` | Unit test JVM (RepeatRules, RewardGuard, Backup, Bridge contract) |
| `godot --headless --path app/src/main/assets -s "$PWD/tests/godot/run_tests.gd"` | Smoke test dunia 3D |
| `gradle connectedDebugAndroidTest` | Uji instrumented (Keystore, transport bridge, idempotensi reward) - perlu perangkat/emulator |

## Push ke GitHub

```bash
git init
git add .
git commit -m "Zahra v0.14.0"
git branch -M main
git remote add origin https://github.com/<username>/<repo>.git
git push -u origin main
```

Lalu buka tab **Actions**: workflow Android dan Godot akan jalan otomatis dan menunjukkan error build yang tersisa.

## Pembaruan v0.16.0 — game lebih rapi dan nyaman

- Karakter pemain/NPC diperhalus dengan proporsi kepala, badan, tangan, kaki, rambut, dan wajah yang lebih seimbang; animasi langkah dibuat lebih tenang. Ini tetap gaya 3D stylized, bukan manusia fotorealistis.
- Rumah diberi pintu, jendela, lis, jalur, dan koneksi jalan sederhana.
- Teks tutorial dan HUD dirapikan; obrolan acak yang terasa repetitif dikurangi; emoji dekoratif di HUD dihapus.
- Render pixel ratio dibatasi ke 1.5 dan shadow map 512 untuk membantu performa ponsel.
- Game mencoba memuat Three.js dari dua CDN. Karena Three.js tidak ada di arsip sumber dan akses jaringan build tidak tersedia, game 3D belum offline/self-contained; game memerlukan koneksi untuk memuat library, kecuali `three.min.js` ditambahkan secara lokal.
- Manifest menambahkan izin INTERNET untuk pemuatan library game.

## Pembaruan v0.15.0

- Dashboard Android ditata ulang menjadi pusat aktivitas: hero dunia Zahra, kartu Goodness Points dan misi selesai, progress, akses cepat, daftar misi aktif, empty state, serta shortcut pengaturan/backup.
- Build metadata diperbarui ke `versionCode 15` / `versionName 0.15.0`.
- Perilaku data, Room, backup terenkripsi, kamera proof, reminder, dan bridge tidak diganti pada perubahan visual ini.
- Catatan game: `app/src/main/assets/game/index.html` merujuk `three.min.js`, tetapi file tersebut tidak ada pada ZIP sumber. Perlu vendoring Three.js untuk game berjalan sepenuhnya offline; aplikasi utama tetap dapat dibangun terpisah dari game.

## Fitur

- Onboarding aman, profil, misi (buat/ubah/selesai/jeda/lanjut/arsip), pengulangan, jadwal tanggal-jam, daftar/checklist.
- Goodness Points + reward berambang, ledger idempoten (satu sumber poin = satu baris).
- Pengingat opt-in (WorkManager) yang disinkronkan ulang saat reboot, ganti jam, ganti zona waktu.
- Bukti kamera: foto, pose (ML Kit), objek (ML Kit). AI hanya memberi evidence; keputusan akhir tetap pada pengguna.
- Backup terenkripsi portabel (password, PBKDF2 + AES-GCM) dan restore dengan validasi.
- Dunia simulasi 3D berbasis Three.js: karakter pemain, NPC, kebun, pasar, masjid, balai warga, event dan sistem ekonomi ringan.
- Jembatan app <-> game v2: terautentikasi (Android Keystore HMAC), antrean inbox/outbox, event ID unik.

## Kosmetik dan pembagian poin

Poin aplikasi (nyata) dan uang game (virtual) sengaja dipisah; koneksi bersifat opsional.


## Mulai cepat di GitHub

Folder ini adalah **root repository**. Upload/commit isi ZIP ini langsung ke root repo (jangan bungkus lagi dengan folder `src/`). Buka folder proyek ini di Android Studio atau jalankan workflow GitHub Actions dari tab **Actions**.

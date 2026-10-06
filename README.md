# Zahra v0.14.0

Aplikasi pribadi (Android) untuk misi, daftar, reward, dan bukti kamera, ditambah **dunia 3D simulasi kehidupan** (Godot) yang tertanam di dalam APK yang sama.

> Status jujur: kode ini **belum pernah dikompilasi atau dijalankan** (tidak ada Android SDK/Gradle/Godot di lingkungan pembuatnya). v0.14.0 memperbaiki 19 kelompok kesalahan yang pasti menggagalkan build/parse (lihat `docs/VERIFICATION_REPORT_V0.14.0.md`). Build pertama kemungkinan masih menemukan beberapa error kecil. CI di `.github/workflows` disiapkan supaya error itu langsung kelihatan.

## Struktur repo

```
.
├── app/                         Modul Android (Kotlin, Jetpack Compose, Room, WorkManager, CameraX, ML Kit)
│   └── src/main/assets/         Proyek Godot 4.7 (project.godot, scenes/, scripts/world.gd) -> ikut dikemas ke APK
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

## Fitur

- Onboarding aman, profil, misi (buat/ubah/selesai/jeda/lanjut/arsip), pengulangan, jadwal tanggal-jam, daftar/checklist.
- Goodness Points + reward berambang, ledger idempoten (satu sumber poin = satu baris).
- Pengingat opt-in (WorkManager) yang disinkronkan ulang saat reboot, ganti jam, ganti zona waktu.
- Bukti kamera: foto, pose (ML Kit), objek (ML Kit). AI hanya memberi evidence; keputusan akhir tetap pada pengguna.
- Backup terenkripsi portabel (password, PBKDF2 + AES-GCM) dan restore dengan validasi.
- Dunia 3D: satu karakter, NPC berjadwal, ekonomi, bisnis, politik, event dinamis, peta kota, simpanan terkompresi + integritas.
- Jembatan app <-> game v2: terautentikasi (Android Keystore HMAC), antrean inbox/outbox, event ID unik.

## Kosmetik dan pembagian poin

Poin aplikasi (nyata) dan uang game (virtual) sengaja dipisah; koneksi bersifat opsional.

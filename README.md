# Zahra v0.18.0 — Calm Islamic companion & yearly growth

Aplikasi Android untuk ibadah dan aktivitas harian, misi otomatis, konten hadis/doa daring, level tahunan tersimpan, kebun benih yang tumbuh dari reward, serta teman virtual Mimi. Visual memakai warna sage, teal, lavender, dan putih hangat; tidak ada estetika cyberpunk.

> Status verifikasi: audit statis tersedia, tetapi paket ini belum berhasil di-build menjadi APK di lingkungan kerja ini karena Gradle/Android SDK tidak tersedia. Jalankan build dan unit test di Android Studio/CI sebelum instalasi final.

## Struktur repo

```
.
├── app/                         Modul Android (Kotlin, Jetpack Compose, Room, WorkManager, CameraX, ML Kit)
│   └── src/main/assets/         Asset lokal aplikasi
├── tools/audit/                 Audit statis tanpa kompiler (python3 tools/audit/audit_all.py)
├── docs/                        Catatan arsitektur, status, dan laporan verifikasi
├── gradle/wrapper/              gradle-wrapper.properties (Gradle 9.4.1)
└── .github/workflows/           android.yml (unit test + APK debug)
```

## Cara build

Prasyarat: JDK 17+, Android SDK platform 37, Android Studio terbaru.

1. Buka folder ini di Android Studio, tunggu sync. (Studio memakai `gradle/wrapper/gradle-wrapper.properties` -> Gradle 9.4.1.)
2. Opsional untuk terminal: `gradle wrapper --gradle-version 9.4.1` sekali saja (membuat `gradlew` dan `gradle-wrapper.jar`), lalu commit.
3. `./gradlew testDebugUnitTest assembleDebug`
4. APK ada di `app/build/outputs/apk/debug/`.

Catatan versi: AGP 9.2 memakai Kotlin bawaan. **Jangan** menambahkan plugin `org.jetbrains.kotlin.android`; gunakan konfigurasi plugin yang sudah ada di root Gradle.

## Pemeriksaan

| Perintah | Fungsi |
|---|---|
| `python3 tools/audit/audit_all.py` | Audit statis untuk beberapa kesalahan Kotlin/Compose dan metadata Gradle |
| `gradle testDebugUnitTest` | Unit test JVM (RepeatRules, RewardGuard, Backup) |
| `gradle connectedDebugAndroidTest` | Uji instrumented (idempotensi reward dan data) - perlu perangkat/emulator |

## Push ke GitHub

```bash
git init
git add .
git commit -m "Zahra v0.18.0"
git branch -M main
git remote add origin https://github.com/<username>/<repo>.git
git push -u origin main
```

Lalu buka tab **Actions** dan jalankan workflow **Android CI** untuk membuat APK debug.



## Fitur

- Onboarding, profil, misi ibadah dan aktivitas (buat/ubah/selesai/jeda/lanjut/arsip), pengulangan, dan pengingat.
- Reward tetes air dengan jumlah yang mengikuti tingkat kesulitan misi. Tetes air adalah progres aplikasi, bukan ukuran pahala atau nilai ibadah.
- Mimi bergerak lembut, memiliki pesan penyemangat, dan bisa diberi pakan dari reward misi.
- Pengingat memakai nada dua-nota lembut buatan lokal, getaran ringan, dan kanal notifikasi terpisah.
- Checklist modern dengan status selesai dan indikator progres.
- Kebun dimulai dari benih, tumbuh bertahap sesuai tetes air, dengan animasi daun jatuh dan pesan pribadi.
- Level/EXP tersimpan per tahun kalender, termasuk riwayat tahunan yang dibackup dan dipulihkan.
- Sembilan koleksi hadis dan kategori doa daring; sumber ditampilkan, koneksi internet diperlukan. Pesan pribadi Mimi ditandai bukan hadis.
- Misi awal sholat wajib dan rutinitas sunnah/puasa yang bisa diedit/diarsip; aktivitas buatan pengguna mendapat estimasi kesulitan lokal transparan.
- Palet tenang: hijau sage, teal, lavender, dan putih hangat dengan animasi peralihan halus.
- Bukti kamera: foto, pose (ML Kit), objek (ML Kit). AI hanya memberi evidence; keputusan akhir tetap pada pengguna.
- Backup terenkripsi portabel (password, PBKDF2 + AES-GCM) dan restore dengan validasi.

## Mulai cepat di GitHub

Folder ini adalah **root repository**. Upload/commit isi ZIP ini langsung ke root repo (jangan bungkus lagi dengan folder `src/`). Buka folder proyek ini di Android Studio atau jalankan workflow **Android CI** dari tab **Actions**.


## Arah produk v0.18.0

- Tidak ada game terpisah: beranda memadukan Mimi, kebun kecil, level tahunan, aktivitas dan akses cepat ibadah.
- Tetes air, EXP, pakan dan poin adalah gamifikasi aplikasi, bukan ukuran pahala, kualitas iman, atau hukum ibadah.
- Estimasi kesulitan adalah heuristik lokal yang bisa meleset, bukan model AI yang memahami konteks atau penilaian agama.
- Hadis dan doa diambil dari API pihak ketiga saat layar dibuka; verifikasi rujukan dan penjelasan tetap diperlukan. Konten daring tidak dijamin tersedia offline.
- Build APK dan tes JUnit harus dijalankan di Android Studio/CI karena Gradle executable dan Android SDK tidak tersedia di lingkungan pengeditan ini.

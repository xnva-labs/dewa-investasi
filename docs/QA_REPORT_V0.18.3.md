# Zahra v0.18.3 — QA report

## Perubahan pada source

- Halaman sambutan langsung menawarkan masuk ke aplikasi; sapaan nama bersifat opsional dan usia tidak diminta.
- Beranda menampilkan sapaan sesuai waktu lokal serta satu pesan penyemangat harian yang ditandai sebagai catatan pribadi, bukan hadis.
- Pilihan pengingat salat wajib dan alarm puasa disimpan melalui Preferences DataStore lokal.
- Halaman Surat Kecil menyimpan teks personal di Preferences DataStore lokal dan secara eksplisit memisahkannya dari hadis/doa.
- `versionCode` dinaikkan ke 21 dan `versionName` ke `0.18.3`.

## Pemeriksaan yang dilakukan

- `python3 tools/audit/audit_all.py` — lulus: tidak ada temuan statis.
- Peninjauan source dilakukan pada onboarding, navigasi, beranda, penyimpanan preferensi pengingat, dan penyimpanan catatan pribadi.

## Belum diverifikasi

- APK belum dibangun dalam lingkungan ini karena executable Gradle/Android SDK tidak tersedia.
- JUnit, uji perangkat, perilaku notifikasi setelah reboot, serta UI pada perangkat nyata belum dijalankan.
- Jadwal salat bergantung pada layanan AlAdhan dan koneksi internet; waktu WorkManager dapat tertunda oleh optimasi baterai Android.
- Catatan pribadi dan data kalender siklus berada di penyimpanan lokal aplikasi, tetapi belum dienkripsi secara terpisah. Pengguna sebaiknya memakai kunci layar perangkat dan tidak menganggap data lokal sebagai vault terenkripsi.

## Langkah verifikasi CI

Jalankan `gradle testDebugUnitTest assembleDebug --stacktrace --console=plain` pada runner dengan JDK 17 dan Android SDK platform 37. Pastikan workflow Android CI lulus sebelum memasang APK.

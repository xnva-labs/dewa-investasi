# QA Report Zahra v0.18.5

## Perubahan
- Pengingat sholat/imsak/berbuka meneruskan kunci jenis pengingat dan mengambil jadwal AlAdhan untuk hari berikutnya setiap kali worker berjalan. Jika API gagal, worker memakai fallback jam lokal sebelumnya.
- Repository jadwal menerima offset tanggal agar bisa meminta tanggal besok, zona waktu Asia/Jakarta.
- Catatan siklus (tanggal mulai, panjang siklus, durasi) dienkripsi AES/GCM/NoPadding dengan IV acak dan kunci AES 256-bit di Android Keystore.
- Nilai plaintext lama tetap dibaca untuk kompatibilitas dan dienkripsi ketika pengguna menekan Simpan. Data kosong tetap kosong.

## Pemeriksaan
- Audit statis repository dijalankan setelah perubahan.
- Build Gradle, tes Android, serta notifikasi nyata tidak dapat dikonfirmasi di lingkungan ini tanpa Android SDK/Gradle Wrapper lengkap dan perangkat.

## Catatan batasan
- WorkManager tidak menjamin pengiriman tepat menit karena pembatasan baterai/Doze.
- Jika jaringan gagal saat reschedule, jadwal lama dipakai sebagai fallback sampai worker berikutnya berhasil mengambil API.
- Data siklus dienkripsi saat tersimpan oleh aplikasi; jangan menganggapnya terlindungi dari perangkat yang sudah di-root atau kompromi saat perangkat terbuka.
- DataStore yang berisi data lama plaintext hanya akan menjadi terenkripsi saat pengguna menyimpan ulang catatannya.

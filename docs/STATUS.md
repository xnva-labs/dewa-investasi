# Zahra v0.18.0 status

## Fokus rilis
- Progres/level disimpan terpisah untuk setiap tahun kalender di Room, dan disertakan di backup.
- Reward misi memberi tetes air bagi tanaman, EXP bagi level tahunan, serta pakan Mimi berdasar estimasi kesulitan.
- Misi dasar sholat dan rutinitas sunnah/puasa dibuat otomatis dengan pencegahan duplikasi transaksional.
- Hadis/doa dimuat dari sumber daring pihak ketiga, ditampilkan dengan label sumber.
- Dashboard dan kartu mendapatkan animasi halus; kebun memiliki fase benih, tumbuh, daun, dan daun jatuh.

## Validasi
- Audit statis: jalankan `python3 tools/audit/audit_all.py`.
- Unit test Kotlin: `gradle testDebugUnitTest` bila Gradle terpasang.
- Build APK: `gradle assembleDebug` di Android Studio/CI dengan Android SDK.
- Build penuh belum dapat dikonfirmasi dari lingkungan pengeditan ini karena Gradle executable/wrapper JAR dan Android SDK tidak tersedia.

## Catatan
- Estimasi kesulitan adalah heuristik lokal berbasis judul/deskripsi, bukan AI cloud. Data aktivitas tidak dikirim ke layanan analitik.
- Reward progres adalah gamifikasi, bukan pahala atau pengukuran tingkat keimanan.
- API sumber agama adalah layanan pihak ketiga: konten memerlukan internet dan sebaiknya diverifikasi untuk kajian mendalam.

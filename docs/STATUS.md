# Zahra v0.14.0 status

## Sumber yang diimplementasikan
Sama seperti v0.13.0 (onboarding, misi, daftar, reward, pengingat, bukti kamera, backup terenkripsi, jembatan v2, dunia 3D dengan NPC/ekonomi/bisnis/politik/event/World Brain, save terkompresi) ditambah perbaikan build/parse di `CHANGELOG.md`.

## Terverifikasi di lingkungan pembuatan
- Audit statis `tools/audit/audit_all.py` bersih; pada v0.13.0 alat yang sama menemukan seluruh kesalahan yang diperbaiki.
- Keseimbangan tanda kurung Kotlin/GDScript dan indentasi GDScript (kelipatan 4 spasi, tanpa tab) diperiksa dengan skrip.
- Dicek ke dokumentasi/rilis resmi: AGP 9.2 (Gradle >= 9.4.1, JDK 17, tanpa plugin kotlin-android), Godot Android library (proyek di assets), KSP 2.3.12, Compose 1.12 + Material3 1.4.0, Godot 4.7.2. Versi AndroidX lain (activity, navigation, lifecycle, room, datastore, work, core, camera) dan ML Kit mengikuti pilihan v0.13.0 dan belum dicek ulang.

## Belum terverifikasi (butuh mesin dengan toolchain)
- Kompilasi Kotlin dan assemble APK (tidak ada kompiler Kotlin/Gradle/Android SDK di lingkungan pembuatan).
- Parse dan runtime Godot (tidak ada executable Godot); `tests/godot/run_tests.gd` disiapkan untuk itu.
- Uji instrumented, jembatan app<->game di perangkat, profil performa/baterai, penandatanganan rilis.
- Kemungkinan masih ada error kecil yang hanya terlihat oleh kompiler sungguhan (mis. tipe di Room/Compose). Gunakan CI untuk menemukannya.

## Langkah berikutnya yang disarankan
1. Push ke GitHub, baca log Actions, perbaiki error build yang tersisa.
2. Pecah `world.gd` (2.200+ baris) menjadi beberapa skrip/autoload agar bisa diuji per bagian.
3. Tambahkan uji migrasi Room memakai skema di `app/schemas/` setelah build pertama.
4. Isi celah konsep (interior bangunan, resep/inventaris, kustomisasi karakter, adzan/kalender) satu sistem per rilis.

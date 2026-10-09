# Laporan verifikasi v0.14.0

Metode: pembacaan baris-per-baris seluruh Kotlin (±3.000 baris) dan `world.gd` (2.240 baris) pada v0.13.0, pengecekan dokumentasi resmi, serta skrip audit statis. Tidak ada kompiler yang tersedia, jadi laporan ini adalah *bukti pembacaan*, bukan hasil build.

## Temuan dan perbaikan

| # | Area | Temuan di v0.13.0 | Dampak | Perbaikan |
|---|---|---|---|---|
| 1 | Gradle | Plugin `org.jetbrains.kotlin.android` + AGP 9.2 | Konfigurasi gagal ("no longer required for Kotlin support since AGP 9.0") | Plugin dihapus, KGP dipaksa 2.4.20 via buildscript |
| 2 | Gradle | `kotlinOptions { }` | Tidak ada di DSL baru AGP 9 | `kotlin { compilerOptions }` |
| 3 | Room | `proofDao()` tidak ada di `ZahraDatabase` | Compile error di AppRepository/BackupRepository | Ditambahkan |
| 4 | Compose | `import ...layout.weight` | Unresolved reference | Dihapus |
| 5 | Compose | `Button(Boolean, {...}, Modifier)` x2, `Button(Modifier, onClick=)`, `ElevatedCard(Modifier, onClick=)` | Type mismatch / overload tak cocok | Argumen bernama |
| 6 | Compose | API Material3 eksperimental tanpa opt-in | Error opt-in | `@file:OptIn` |
| 7 | Navigasi | `startDestination` berubah setelah profil terbaca | Layar sambutan berkedip, grafik di-reset | `produceState` sekali |
| 8 | Test | `BackupContractTest` mengharapkan skema 4 (nilai 5) | `testDebugUnitTest` gagal | Diselaraskan |
| 9 | Test | `org.json` stub di unit test | `GameBridgeContractTest` gagal | `org.json:json` di test classpath |
| 10 | Bridge | Event duplikat tidak dihapus | Diproses ulang tiap onResume | Dihapus bila duplikat, dipertahankan bila DB gagal |
| 11 | Bridge | `activity` nullable | Potensi compile error | `requireNotNull(activity)` |
| 12 | Godot | `String.take()` | Parse error: skrip tidak termuat | `left()` |
| 13 | Godot | 13x `:=` dari Variant | Parse error | Tipe eksplisit |
| 14 | Godot | `used_legacy` tak dideklarasikan | Parse error | Dideklarasikan |
| 15 | Godot | Tombol sentuh di luar layar | Tidak bisa bergerak di ponsel | Anchor + offset |
| 16 | Godot | Pemain/rumah melayang | Visual salah | Rata dengan tanah |
| 17 | Godot | Jalur NPC ke sel solid | NPC tidak pernah sampai; A* tiap frame | Sel terbuka terdekat + jeda |
| 18 | Godot | Siang/malam tak jalan | Dunia selalu terang | Referensi node nyata |
| 19 | Godot | Viewport 1280x720 tanpa stretch pada orientasi portrait | Teks 13 px sangat kecil | 720x1280 + canvas_items |

## Yang masih perlu build sungguhan
Lihat `STATUS.md`. Alat audit tidak menggantikan kompiler; ia hanya menaikkan peluang build pertama berhasil dan menyederhanakan diagnosis.

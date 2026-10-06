# CHANGELOG

## v0.14.0 - perbaikan build/parse + jalur CI
Audit statis terhadap v0.13.0 menemukan kesalahan yang pasti menggagalkan build atau memuat skrip. Semuanya diperbaiki:

Android / Gradle
- Plugin `org.jetbrains.kotlin.android` dihapus (AGP 9 membawa Kotlin sendiri; plugin lama membuat konfigurasi gagal). `kotlinOptions{}` diganti `kotlin { compilerOptions }`. KGP dipaksa 2.4.20 lewat `buildscript` agar sama dengan plugin Compose.
- `ZahraDatabase` tidak mengekspos `proofDao()` padahal dipakai `AppRepository` dan `BackupRepository` -> ditambahkan.
- `import androidx.compose.foundation.layout.weight` (tidak ada) dihapus; `@file:OptIn(ExperimentalMaterial3Api::class)` ditambahkan.
- `Button`/`OutlinedButton` dengan argumen posisi yang salah (Boolean/Modifier sebagai argumen pertama) di layar Backup dan Kamera; `ElevatedCard(Modifier, onClick=)` -> urutan benar.
- `LocalLifecycleOwner` memakai paket lifecycle (yang lama deprecated).
- Layar sambutan tidak lagi berkedip untuk pengguna lama (tujuan awal NavHost ditentukan dari database).
- `ZahraGodotPlugin`: `activity` bisa nullable di sisi Java -> akses aman.
- `GameBridge`: file event duplikat sekarang dihapus (sebelumnya tertinggal selamanya dan diproses ulang).
- `AppRepository.resumeMission`: tipe `Long?` eksplisit agar smart-cast aman.
- Test: `BackupContractTest` menguji skema 4 padahal 5; `org.json` ditambahkan ke classpath unit test (stub android.jar membuat `GameBridgeContractTest` gagal).
- Room: `room.schemaLocation` dikonfigurasi; dependency `lifecycle-viewmodel-compose` eksplisit.
- Versi: versionName 0.14.0 / versionCode 14.

Godot (world.gd)
- `String.take()` bukan API Godot -> `left()` (parse error).
- 13 deklarasi `:=` dari nilai Variant (`max/min/clamp/abs`, indeks dictionary/array) -> tipe eksplisit atau `maxf/clampf/absf`.
- `used_legacy` dipakai tanpa dideklarasikan -> dideklarasikan.
- Tombol sentuh kanan-bawah berada di luar layar (anchor + posisi negatif) -> memakai offset.
- Pemain melayang 1 m dan rumah mengambang -> semuanya rata dengan tanah.
- NPC tidak pernah sampai ke bangunan (tujuan di sel solid grid -> jalur kosong dihitung ulang tiap frame) -> sel terbuka terdekat + jeda hitung ulang.
- Siklus siang-malam tidak pernah berjalan (node dicari lewat nama yang salah) -> referensi node nyata, malam benar-benar gelap, hujan meredupkan.
- `project.godot`: `config_version=5`, viewport portrait 720x1280 dengan stretch `canvas_items` (teks tidak lagi mikroskopis).

Infrastruktur
- `.gitignore`, `.gitattributes`, `gradle-wrapper.properties` (Gradle 9.4.1).
- CI: `android.yml` (unit test + APK debug), `godot.yml` (smoke test headless Godot 4.7.2).
- `tests/godot/run_tests.gd`: parse semua skrip + menjalankan dunia 3D 3 hari dengan semua aksi HUD.
- `tools/audit/`: audit statis yang terbukti menangkap seluruh kesalahan di atas pada v0.13.0.

## v0.12.0
- Added a lightweight state-driven World Brain for economy, business, politics and city state.
- Added inflation, employment, consumer confidence, business confidence, supply stability, production and city activity metrics.
- Added faction opinion model for business, workers, youth, public and community groups.
- Added political influence, political capital, trust, heat, policy approval and election cycle state.
- Added dynamic business sector and pricing strategy controls.
- Added market research, supplier/business negotiation, community meetings, political strategy and campaign actions.
- Added dynamic event scoring, event cooldowns, contextual economic/political events and business opportunities.
- Added activity memory plus boredom/novelty balancing to reduce repetitive gameplay.
- Added city infrastructure, services, transport quality, business density and public order feedback loops.
- Reworked daily business revenue to react to confidence, demand, competition, strategy and sector.
- Upgraded game save schema to version 8 and switched default save storage to compressed ZSTD binary using Godot FileAccess.
- Added migration from the previous plaintext JSON save when present.
- Bumped Android app version to 0.12.0 / versionCode 12.

## v0.11.0
- Added editable mission records without deleting historical IDs.
- Added safe mission update rescheduling.
- Added absolute date/time validation for mission schedules.
- Added mission pause history events and list toggle result validation.
- Kept portable backup and game health/business systems.

## v0.13.0 - hardening + world deepening
- Authenticated, durable Android ↔ Godot bridge protocol v2 with explicit inbox/outbox direction and event-ID-bound signatures.
- Transactional, idempotent mission reward ledger and recurring-cycle guard.
- Save integrity sidecar using SHA-256 + Android Keystore HMAC, with backup recovery and safer pair-commit rollback.
- Fixed Android→Godot mission-event queue direction and hardened the NPC goal-state assignment regression.
- Lifecycle/clock/timezone reminder resynchronization.
- NPC trust, goals, bounded memory and peer interaction drift.
- Macro economy feedback for wages, purchasing pressure and market volatility.
- Policy budget costs, coalition strength, consultation and policy debt.
- Election difficulty tied to political state.
- Consequence-chain World Brain events.
- Bounded debug performance telemetry.

# CHANGELOG

## v0.18.0 — Calm annual growth
- Added persisted annual levels/EXP/water/cat-food/plant progress with Room migration and backup/restore support.
- Added difficulty estimation, difficulty-scaled water/EXP/cat food, and growing plant/leaf milestones with clearly labeled private notes.
- Added race-safe starter mission seeding for obligatory prayers and selected sunnah practices, including Monday/Thursday and alternating-day Daud schedules.
- Added online hadith collection lookup and categorized dua list with visible source labels and graceful retry state.
- Added calm Mimi float motion, garden growth/falling leaves, non-looping per-stage progress, subtle card transitions, and a distinct gentle reminder channel.
- Added progression/heuristic and Puasa Daud unit tests.
- Full Gradle build remains unverified in this environment; run Android CI or Android Studio before release.


## v0.14.0-fix2
- Build CI: `compileSdk` 36 -> 37 (core-ktx 1.19.x). Dependensi Godot dicabut seluruhnya (sumber risiko build terbesar); proyek Godot dipindah ke `legacy-godot/`.
- Game 3D kini three.js di WebView (`GameActivity`, `assets/game/index.html`, `GameJsBridge`): kebun, pasar, restoran, masjid, balai, 6 NPC, misi amal, politik. `three.min.js` diunduh CI sebelum build (lihat android.yml); build lokal: unduh manual ke `app/src/main/assets/game/`.
- Misi selesai di app -> pahala di game. Amal di game -> tercatat di History app.
- Dashboard: kartu "Dunia Zahra" dengan tombol masuk. Misi: 12 template misi ibadah harian.
- Game: jalan dan pagar kebun, bunga, tangan dan mata karakter, menu salad premium, nada saat waktu sholat tiba, bintang restoran (+pahala), uji asap Node (13 hari simulasi) lolos.
- CI: log lengkap (`build.log`) dan ringkasan penyebab gagal di tab Summary.

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

## v0.16.0 — Game polish
- Proporsi karakter stylized dan gerak tangan/kaki dirapikan.
- Rumah dilengkapi pintu/jendela dan jalur penghubung.
- HUD/tutorial lebih bersih; interaksi NPC acak dikurangi.
- Batas render ponsel disetel lebih konservatif.
- Pemuatan Three.js memakai dua CDN dan pesan fallback yang jelas; offline bundling masih belum terpenuhi.

## v0.18.1 — prayer reminders and CI compile fix
- Added file-level Material 3 experimental API opt-in to screens using Material 3 experimental components, fixing the compiler diagnostic for `CenterAlignedTopAppBar`.
- Added a Cirebon prayer timetable screen backed by AlAdhan city timings and a visible calculation-method/source caveat.
- Added opt-in WorkManager notifications for the five daily prayers and optional sahur (30 minutes before imsak) and Maghrib/iftar. Reminders repeat daily and use the app's calm notification channel.
- Added notification permission request flow, cancellation of reminders when switched off, and dashboard shortcuts.
- Reminder delivery is battery-aware/inexact; users should cross-check local mosque times.

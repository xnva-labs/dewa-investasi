## Revisi v0.18.6
- Versi aplikasi: 0.18.6 (versionCode 24).
- Navigasi bawah berada di scaffold utama, menyorot route aktif yang benar, dan hanya tampil di empat layar utama.
- Perpindahan tab memakai singleTop serta save/restore state untuk mengurangi penumpukan back stack.
- Tes unit regresi: route tab utama dipetakan dengan benar; sambutan dan layar detail tidak memetakan tab aktif.

# Zahra v0.13.0 implementation matrix

Legend: ✅ source implemented, 🧪 statically checked, ⬜ runtime/device pending.

| System | Source | Static | Runtime/device |
|---|---:|---:|---:|
| Profile + safe onboarding | ✅ | 🧪 | ⬜ |
| Missions | ✅ | 🧪 | ⬜ |
| Calendar scheduling | ✅ | 🧪 | ⬜ |
| Recurrence | ✅ | 🧪 | ⬜ |
| Goodness Points | ✅ | 🧪 | ⬜ |
| Rewards | ✅ | 🧪 | ⬜ |
| Flexible lists | ✅ | 🧪 | ⬜ |
| Reminders | ✅ | 🧪 | ⬜ |
| Camera proof | ✅ | 🧪 | ⬜ |
| Portable backup/restore | ✅ | 🧪 | ⬜ |
| Game bridge + authenticated event transport | ✅ | 🧪 | ⬜ |
| 3D world/player | ✅ | 🧪* | ⬜ |
| NPC/schedules + memory/trust/goals | ✅ | 🧪* | ⬜ |
| Economy/world simulation + macro feedback | ✅ | 🧪* | ⬜ |
| Business simulation | ✅ | 🧪* | ⬜ |
| Political/government simulation + coalition/policy debt | ✅ | 🧪* | ⬜ |
| Dynamic events/opportunities + consequence chains | ✅ | 🧪* | ⬜ |
| Anti-repetition/boredom control | ✅ | 🧪* | ⬜ |
| Save/load + compressed ZSTD + integrity signature/recovery | ✅ | 🧪* | ⬜ |
| Performance safeguards + debug telemetry | ✅ | 🧪* | ⬜ |

*Godot runtime is not installed in this environment, so these remain source-level checks until a Godot/Android build environment is available.


## Revisi v0.18.3

| Perubahan | Source | Static | Runtime/device |
|---|---:|---:|---:|
| Sambutan tanpa formulir nama/usia wajib | ✅ | 🧪 | ⬜ |
| Sapaan berdasarkan waktu lokal + pesan harian non-hadis | ✅ | 🧪 | ⬜ |
| Preferensi pengingat salat/puasa tersimpan lokal | ✅ | 🧪 | ⬜ |
| Surat kecil personal tersimpan lokal dan terpisah dari konten agama | ✅ | 🧪 | ⬜ |
| APK dan tes unit pada Android SDK/CI | ⬜ | — | ⬜ |


## Revisi v0.18.5

- Pesan pribadi dienkripsi dengan AES-GCM; kunci dibuat dan disimpan oleh Android Keystore. Catatan plaintext dari versi lama dimigrasikan saat disimpan ulang.
- Versi aplikasi: 0.18.5 (versionCode 23).
- Audit statis lulus; build Android masih harus dikonfirmasi melalui CI atau Android Studio.


## Revisi v0.18.5
- Pengingat sholat dan puasa kini mengambil jadwal untuk hari berikutnya dari AlAdhan setelah notifikasi berjalan, dengan fallback jam lokal ketika jaringan gagal. WorkManager tetap dapat terlambat sesuai kebijakan baterai Android.
- Catatan kalender haid dienkripsi saat disimpan menggunakan AES-GCM dan kunci Android Keystore. Data plaintext versi lama dibaca untuk kompatibilitas lalu dienkripsi saat disimpan ulang.

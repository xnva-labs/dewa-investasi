# Zahra Release Gate

| Gate | Current source checkpoint | Evidence required before release |
|---|---|---|
| Android source integrity | Implemented | Gradle compile + unit tests |
| Room migrations | Implemented through v6 | Instrumented migration test + upgrade-path test |
| Bridge authentication + direction | Implemented v2, app→game inbox and game→app outbox | Physical-device round trip |
| Reward idempotency | Implemented | Rapid/stale UI E2E test |
| Save integrity | Implemented with signed current saves + v0.12 migration path | Tamper + backup recovery + legacy migration test |
| Reminder lifecycle | Implemented | Boot/timezone/clock E2E test |
| NPC simulation | Deepened | Godot runtime playtest |
| Dynamic economy | Deepened | Multi-day simulation playtest |
| Political simulation | Deepened | Election/policy consequence playtest |
| Event Director | Deepened | Multi-day event-chain playtest |
| FPS/RAM/thermal | Instrumented in debug | Physical-device profile |
| APK/AAB | Not verifiable in current environment | Actual Android build |
| Godot parser/export | Not verifiable in current environment | Actual Godot export |
| Release signing/install | Not verifiable in current environment | Signed install/upgrade test |

## Penambahan v0.14.0

| Gate | Status | Bukti yang dibutuhkan |
|---|---|---|
| Audit statis bersih | Lulus (`tools/audit/audit_all.py`) | - |
| CI Android (unit test + assembleDebug) | Disiapkan, belum dijalankan | Run hijau di GitHub Actions |
| CI Godot (parse + smoke 3 hari) | Disiapkan, belum dijalankan | Run hijau di GitHub Actions |
| Skema Room diekspor | Dikonfigurasi (`app/schemas`) | Commit skema setelah build pertama |

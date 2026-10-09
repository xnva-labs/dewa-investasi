# Zahra v0.15.0 status

## Fokus rilis
App-first polish: dashboard Compose lebih terstruktur, indikator progress, akses cepat, empty state, serta metadata versi 0.15.0.

## Validasi yang tersedia
- `python3 tools/audit/audit_all.py`: audit statis source Android.
- Build APK/unit test: belum dijalankan di lingkungan ini (Gradle dan Android SDK tidak tersedia).

## Risiko diketahui
- `app/src/main/assets/game/index.html` memanggil `three.min.js`, tetapi file dependensi tidak disertakan dalam ZIP sumber. Game WebView membutuhkan library Three.js lokal untuk mode offline; ini tidak menghalangi pengembangan UI aplikasi, tetapi game belum dapat dianggap siap rilis.
- `docs/STATIC_CHECKS.sh` pada sumber lama merujuk path `app/src/main/assets/scripts/world.gd` yang tidak ada di ZIP fix3 (game pada ZIP ini menggunakan Three.js/HTML).

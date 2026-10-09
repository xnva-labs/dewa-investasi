# QA Report Zahra v0.18.6

## Bug yang diperbaiki
- Tab bawah sebelumnya hanya dirender di Dashboard, sehingga menghilang saat masuk Misi/Progres/Profil.
- Tab Beranda sebelumnya selalu tampak terpilih karena kondisi selection tidak memeriksa route aktif.
- Tombol Beranda sebelumnya tidak melakukan navigasi dari layar lain.

## Perubahan implementasi
- Bottom navigation dipindahkan ke scaffold utama `ZahraApp`.
- Route aktif diamati melalui `currentBackStackEntryAsState()`.
- Tab Beranda, Misi, Progres, Profil masing-masing menyorot dirinya sendiri.
- Navigasi utama menggunakan `launchSingleTop`, `restoreState`, dan `popUpTo("dashboard") { saveState = true }`.
- Navigasi bawah hanya terlihat di empat route utama; sambutan dan halaman detail tidak menampilkan atau menyorot tab utama.
- Ditambahkan `NavigationRouteTest` untuk mengunci perilaku pemetaan route.

## Pemeriksaan
- Audit statis dijalankan di source yang diperbarui.
- ZIP diuji dengan `unzip -t` setelah dibuat.
- Build Gradle/unit test tidak bisa diklaim lulus apabila tidak dijalankan dengan toolchain Android lengkap; hasil CI tetap diperlukan untuk konfirmasi kompilasi dan uji runtime.

# SmartEyeX: audit alur app dan kesesuaian dengan jiwa

Versi source 0.4.2 (Fixed22). Dasar penilaian: source code dan blueprint "jiwa SmartEyeX". Status ini adalah audit kode; build Android, layanan produksi, dan pengujian perangkat harus diverifikasi terpisah.
Status: ✅ sudah ada di source, ⚠️ sebagian/bergantung konfigurasi eksternal, ❌ belum diimplementasikan.

## 1. Alur dari app dibuka

1. **Loading** (animasi pembuka).
2. **PIN** hanya jika PIN diaktifkan (4 digit, tanpa biometrik). App terkunci lagi otomatis setelah 5 menit di latar belakang.
3. **Aktivasi** ("TAP TO ACTIVATE"). Pada peluncuran pertama langsung lanjut ke **Profile** sekali (perkenalan), berikutnya ke dashboard.
4. **Dashboard sistem** dengan 15 node: Profile, Notification Listener, XNAI Core, Memory, Computer Vision, Media Assistant, Translation, Device, Emergency, Navigation, Call, Enterprise, Perpustakaan, Progress, Schedule.
5. Tombol kembali selalu ke dashboard. Pengaturan privasi (Privacy Control, PIN, kebijakan privasi) dibuka dari dashboard.

Perkenalan sekali lewat Profile sudah ada, tetapi belum ada isian umur/status terpisah, scan sidik jari/wajah, dan rekam suara seperti di blueprint bagian 1.

## 2. Pemeriksaan fitur

| Fitur | Status | Catatan |
|---|---|---|
| Profile | ✅ | 5 isian (Name, Interest, Habit, BirthInfo, Kelas) masuk ke memory bila Memory ON. |
| Notification Listener | ✅ | Feed, mode GETAR/DERING/SPEAK/SENYAP, prioritas, balas lewat tombol balas notifikasi. |
| XNAI Core | ✅ | Chat 3 mode berpikir, suara tekan-bicara, perintah suara. Butuh server XNAI atau `GEMINI_API_KEY`, dan Cloud Processing ON. |
| Mic Live (baru) | ✅ | Mic selalu aktif, kata panggil "SmartEyeX", matikan lewat tombol atau ucapan. Lihat bagian 4. |
| Memory | ✅ | Catatan dan preferensi, terenkripsi di perangkat, bisa dihapus. |
| Computer Vision | ⚠️ | Foto + analisis/OCR dan Live Monitor sampling opt-in sekitar 3 detik per frame. Bukan video nonstop; Live bergantung backend `/vision` yang menghormati instruksi tambahan. Butuh backend XNAI HTTPS di build produksi. |
| Media Assistant | ✅ | Kontrol musik/podcast lewat izin Notification Access. |
| Translation | ✅ | Butuh server XNAI atau `GEMINI_API_KEY`. |
| Device | ✅ | Baterai, koneksi, status izin. |
| Emergency | ⚠️ | Satu kontak, membuka dialer (sengaja, untuk konfirmasi). Kirim lokasi belum ada. |
| Navigation | ✅ | Menyerahkan tujuan ke Google Maps. |
| Call | ✅ | Kontak cepat, membuka dialer. |
| Enterprise | ✅ | Papan tugas lokal satu perangkat. |
| Perpustakaan, Progress | ✅ | Daftar belajar dan ringkasan kemajuan. |
| Schedule | ✅ | Pengingat dengan alarm tepat waktu, hidup lagi setelah reboot. |
| Privacy Control | ✅ | Izin per fitur, PIN, hapus semua. Tidak ada pipeline pengenalan wajah; Device melaporkan fitur itu belum tersedia. |

## 3. Kesesuaian dengan "jiwa" SmartEyeX

| Bagian blueprint | Status | Kondisi di app |
|---|---|---|
| Kepribadian, gaya bicara, aturan emas (bagian 0 sampai 4) | ✅ baru | Dulu hanya string gaya pendek ("warmth=0.7 ..."). Sekarang `SoulPrompt` (jiwa tetap) ikut di setiap permintaan XNAI, jadi tidak bergantung pada server mana yang menjawab. |
| Spektrum emosi curhat | ⚠️ | `EmotionEngine` membaca kata kunci (senang, sedih, kesal, takut, dll.) dan energi/tempo suara. Belum membedakan kosong/mati rasa. Panduan respons per emosi kini ada di jiwa. |
| 5 keadaan batin (observe, respond, care, initiate, protect) | ⚠️ | Observe dan respond ada secara alami. Care hanya lewat prompt. Initiate belum ada: `proactivity` tersimpan tapi tidak dipakai. Protect ada pada konfirmasi sebelum kirim dan Privacy Control. |
| Mode CompanionMode (Friend, Teacher, Mentor, Researcher, Engineer, Coach, Silent) | ✅ di source | Selector di XNAI CORE mengubah konteks yang dikirim ke XNAI. Persistensi mengikuti izin Memory; tanpa Memory mode berlaku selama sesi. |
| Mode senyap | ⚠️ | Companion SENYAP menekan ucapan otomatis; mode delivery notifikasi (GETAR/SPEAK/DERING/SENYAP) tetap pengaturan terpisah. Jawaban XNAI teks tetap tersedia di layar. |
| Baca notifikasi, jawab lewat suara | ✅ | Pesan dibacakan lengkap dengan nama pengirim. "jawab dek zaa ..." lalu SmartEyeX mengulang isinya dan bertanya "Kirim?". Kirim hanya setelah "iya". Sesuai blueprint: tidak pernah kirim tanpa konfirmasi. |
| "Trusted auto-reply" (kirim tanpa konfirmasi untuk orang tertentu) | ❌ | Sengaja belum dibuat. |
| Meniru gaya pengirim saat membaca (ibu lembut, teman cepat) | ❌ | Suara TTS tunggal. |
| Memory bermakna, bukan log | ⚠️ | Menyimpan catatan, preferensi, pola interaksi ringkas. Belum memori emosional, relasi, atau asosiasi. |
| Face/voice recognition orang lain | ❌ | Tidak ada. |
| Nimbrung sosial, grup chat, prediksi mood orang lain | ❌ | Tidak ada. |
| Time Capsule emosional, Reverse Care, rindu, merelakan | ❌ | Belum ada. Butuh memori emosional jangka panjang dulu. |
| AR overlay, koreksi gerakan, instruktur mesin | ❌ | Live Monitor sampled-frame bukan AR atau sensor keselamatan. AI visual saja tidak dapat memastikan RPM, takaran bahan, suhu, atau status aman mesin. |
| Voice clone pengguna | ❌ | Tidak dibuat. |
| Multi-biometrik (retina, mikro-ekspresi) | ❌ | Tidak dibuat. Perangkat Android biasa tidak mendukungnya dengan andal. |
| Enkripsi end-to-end | ⚠️ | Data lokal terenkripsi (Android Keystore) dan semua trafik HTTPS. Bukan E2E antar-perangkat. |

## 4. Mic Live: ringkasan perilaku

- Nyala dengan ketukan VOICE di layar XNAI. Mati lewat ketukan VOICE, tombol di notifikasi, atau "SmartEyeX matikan mic".
- Hanya ucapan yang diawali "SmartEyeX" yang diproses. Sisanya dibuang dan tidak disimpan.
- `SmartEyeX buka apk <nama>` hanya membuka aplikasi. Dari latar belakang Android memblokir, jadi muncul notifikasi ketuk-untuk-buka.
- Balasan chat selalu lewat konfirmasi "Kirim?".

## 5. Temuan versi 0.4.2 dan batas produksi

- Release build tidak lagi menanamkan `GEMINI_API_KEY`; produksi wajib menggunakan `XNAI_BASE_URL` HTTPS. Gemini direct hanya untuk debug/dev build lokal.
- Backend XNAI tidak termasuk dalam ZIP ini. App memanggil `POST /chat`, `/vision`, dan `/translate`; Live Monitor mengirim `purpose`, `instruction`, dan bila tersedia `previousObservation`. Backend perlu memvalidasi/menggunakan field ini dan mengembalikan `description` untuk Vision.
- Reply-by-voice sekarang menampilkan teks dan penerima untuk ditinjau sebelum mengirim; pengiriman menargetkan aksi notifikasi yang sama dan hanya tersedia ketika notification menyediakan text RemoteInput.
- Kebijakan privasi publik (`docs/privacy-policy.html`) masih template: email kontak, penyedia AI, deskripsi server, dan URL publik harus dilengkapi berdasarkan implementasi produksi yang benar.
- Jadwal pengingat memiliki salinan pemulihan di penyimpanan privat aplikasi yang tidak mendapat enkripsi tambahan; keterbatasan ini sudah dijelaskan dalam teks kebijakan privasi di app dan draft HTML.
- Batas rilis yang belum dapat dibuktikan hanya lewat audit source: build signed AAB, test/lint Android, backend produksi terdeploy dan teruji, izin sistem, QA perangkat, Play Data Safety, dan halaman kebijakan privasi live.

## 6. Kandidat pengembangan setelah alur inti lulus QA

1. Sapaan/proaktivitas yang sopan dan sepenuhnya opt-in.
2. Memori emosional ringkas dengan kontrol retensi dan hapus data.
3. Dukungan Bahasa/aksesibilitas dan retry yang lebih informatif ketika jaringan tidak tersedia.
4. Pengembangan AR/gerakan mesin hanya setelah ada sensor/telemetri dan evaluasi keselamatan; jangan mengandalkan frame AI sebagai satu-satunya pengaman.

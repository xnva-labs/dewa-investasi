# SmartEyeX: audit alur app dan kesesuaian dengan jiwa

Versi app 0.4.0 (Fixed18). Dasar penilaian: kode di zip, dan blueprint "jiwa SmartEyeX" dari chat 2 sampai 5 Februari.
Status: ✅ sudah ada, ⚠️ sebagian, ❌ belum ada.

## 1. Alur dari app dibuka

1. **Loading** (animasi pembuka).
2. **PIN** hanya jika PIN diaktifkan (4 digit, tanpa biometrik). App terkunci lagi otomatis setelah 5 menit di latar belakang.
3. **Aktivasi** ("TAP TO ACTIVATE").
4. **Dashboard sistem** dengan 15 node: Profile, Notification Listener, XNAI Core, Memory, Computer Vision, Media Assistant, Translation, Device, Emergency, Navigation, Call, Enterprise, Perpustakaan, Progress, Schedule.
5. Tombol kembali selalu ke dashboard. Pengaturan privasi (Privacy Control, PIN, kebijakan privasi) dibuka dari dashboard.

Tidak ada layar login atau onboarding berisi umur, status, scan sidik jari/wajah, dan rekam suara seperti di blueprint bagian 1.

## 2. Pemeriksaan fitur

| Fitur | Status | Catatan |
|---|---|---|
| Profile | ✅ | 5 isian (Name, Interest, Habit, BirthInfo, Kelas) masuk ke memory bila Memory ON. |
| Notification Listener | ✅ | Feed, mode GETAR/DERING/SPEAK/SENYAP, prioritas, balas lewat tombol balas notifikasi. |
| XNAI Core | ✅ | Chat 3 mode berpikir, suara tekan-bicara, perintah suara. Butuh server XNAI dan Cloud Processing ON. |
| Mic Live (baru) | ✅ | Mic selalu aktif, kata panggil "SmartEyeX", matikan lewat tombol atau ucapan. Lihat bagian 4. |
| Memory | ✅ | Catatan dan preferensi, terenkripsi di perangkat, bisa dihapus. |
| Computer Vision | ⚠️ | Pratinjau kamera + ambil foto + analisis/OCR. Bukan video live. Butuh server XNAI. |
| Media Assistant | ✅ | Kontrol musik/podcast lewat izin Notification Access. |
| Translation | ✅ | Butuh server XNAI. |
| Device | ✅ | Baterai, koneksi, status izin. |
| Emergency | ⚠️ | Satu kontak, membuka dialer (sengaja, untuk konfirmasi). Kirim lokasi belum ada. |
| Navigation | ✅ | Menyerahkan tujuan ke Google Maps. |
| Call | ✅ | Kontak cepat, membuka dialer. |
| Enterprise | ✅ | Papan tugas lokal satu perangkat. |
| Perpustakaan, Progress | ✅ | Daftar belajar dan ringkasan kemajuan. |
| Schedule | ✅ | Pengingat dengan alarm tepat waktu, hidup lagi setelah reboot. |
| Privacy Control | ✅ | Izin per fitur, PIN, hapus semua. Face Recognition hanya persetujuan, belum ada pipeline. |

## 3. Kesesuaian dengan "jiwa" SmartEyeX

| Bagian blueprint | Status | Kondisi di app |
|---|---|---|
| Kepribadian, gaya bicara, aturan emas (bagian 0 sampai 4) | ✅ baru | Dulu hanya string gaya pendek ("warmth=0.7 ..."). Sekarang `SoulPrompt` (jiwa tetap) ikut di setiap permintaan XNAI, jadi tidak bergantung pada server mana yang menjawab. |
| Spektrum emosi curhat | ⚠️ | `EmotionEngine` membaca kata kunci (senang, sedih, kesal, takut, dll.) dan energi/tempo suara. Belum membedakan kosong/mati rasa. Panduan respons per emosi kini ada di jiwa. |
| 5 keadaan batin (observe, respond, care, initiate, protect) | ⚠️ | Observe dan respond ada secara alami. Care hanya lewat prompt. Initiate belum ada: `proactivity` tersimpan tapi tidak dipakai. Protect ada pada konfirmasi sebelum kirim dan Privacy Control. |
| Mode CompanionMode (Friend, Teacher, SILENT, dll.) | ❌ | Terdefinisi tapi tidak pernah diganti, jadi selalu FRIEND. |
| Mode sekolah/senyap | ⚠️ | Mode SENYAP notifikasi ada. Mic Live kini ikut diam (tidak membacakan pesan) saat SENYAP. Belum ada jawaban via kartu teks. |
| Baca notifikasi, jawab lewat suara | ✅ | Pesan dibacakan lengkap dengan nama pengirim. "jawab dek zaa ..." lalu SmartEyeX mengulang isinya dan bertanya "Kirim?". Kirim hanya setelah "iya". Sesuai blueprint: tidak pernah kirim tanpa konfirmasi. |
| "Trusted auto-reply" (kirim tanpa konfirmasi untuk orang tertentu) | ❌ | Sengaja belum dibuat. |
| Meniru gaya pengirim saat membaca (ibu lembut, teman cepat) | ❌ | Suara TTS tunggal. |
| Memory bermakna, bukan log | ⚠️ | Menyimpan catatan, preferensi, pola interaksi ringkas. Belum memori emosional, relasi, atau asosiasi. |
| Face/voice recognition orang lain | ❌ | Tidak ada. |
| Nimbrung sosial, grup chat, prediksi mood orang lain | ❌ | Tidak ada. |
| Time Capsule emosional, Reverse Care, rindu, merelakan | ❌ | Belum ada. Butuh memori emosional jangka panjang dulu. |
| AR overlay, koreksi gerakan, instruktur mesin | ❌ | Vision belum live. Pemantauan RPM tidak mungkin dari AI cloud. |
| Voice clone pengguna | ❌ | Tidak dibuat. |
| Multi-biometrik (retina, mikro-ekspresi) | ❌ | Tidak dibuat. Perangkat Android biasa tidak mendukungnya dengan andal. |
| Enkripsi end-to-end | ⚠️ | Data lokal terenkripsi (Android Keystore) dan semua trafik HTTPS. Bukan E2E antar-perangkat. |

## 4. Mic Live: ringkasan perilaku

- Nyala dengan ketukan VOICE di layar XNAI. Mati lewat ketukan VOICE, tombol di notifikasi, atau "SmartEyeX matikan mic".
- Hanya ucapan yang diawali "SmartEyeX" yang diproses. Sisanya dibuang dan tidak disimpan.
- `SmartEyeX buka apk <nama>` hanya membuka aplikasi. Dari latar belakang Android memblokir, jadi muncul notifikasi ketuk-untuk-buka.
- Balasan chat selalu lewat konfirmasi "Kirim?".

## 5. Temuan dan keputusan di Fixed18

- Backend XNAI dihapus dari proyek (folder `backend/` dan workflow-nya). App tetap memanggil `POST /chat`, `/vision`, `/translate` di `XNAI_BASE_URL`; server itu sekarang proyek terpisah.
- Kebijakan privasi tidak lagi menyebut penyedia AI atau perilaku server. Ada 3 placeholder yang harus diisi: email kontak, penyedia model AI, dan penjelasan server XNAI.
- Teks di layar Privacy Policy dalam app masih menyebut jadwal tidak disimpan sebagai teks biasa, padahal salinan pemulihan jadwal tersimpan tanpa enkripsi tambahan. Perlu disamakan.
- Mic Live tetap jalan saat app terkunci PIN (terkunci otomatis setelah 5 menit di latar belakang). Ini disengaja agar hands-free berfungsi; balasan chat dilindungi konfirmasi.

## 6. Usulan langkah berikutnya (berurutan)

1. Aktifkan keadaan "initiate" yang sopan (sapaan balik hanya saat pengguna santai, berhenti jika tidak dibalas).
2. Wiring `CompanionMode` dan kartu teks untuk mode senyap.
3. Memori emosional ringkas (pola, bukan kutipan) dengan batas retensi.
4. Vision live dengan 1 frame tiap 1 sampai 2 detik, setelah server XNAI baru siap.

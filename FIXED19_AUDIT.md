# SmartEyeX fixed19 — persona XNAI + gerbang usia (tahun lahir)

Baseline: fixed18 (kamu laporkan berhasil build). Hardware dan Play Store tetap ditunda.

## Backend (`backend/`, `npm test` -> 43/43 lulus, `npm run eval` butuh kunci)
- **Persona Gen-Z** server-side (`persona.mjs`): inti nilai dari dokumen jiwa (observe-first, tanya dulu, jujur dia AI, peduli tanpa posesif, berani menolak sambil hadir,
  tidak bikin tergantung), gaya mengikuti user (gue/lo vs aku/kamu vs formal), singkat untuk suara, nada lebih pelan malam hari (app kirim `localHour`).
- **Tiga kelompok usia** lewat header `X-Age-Band`: ADULT / TEEN (tanpa romantis/seksual, tanpa konteks memori, dorong ke orang dewasa tepercaya, vision cloud ditolak) / CHILD (403). Kosong/tak dikenal = TEEN.
- **Mode krisis** + **fallback tetap** bila model/kuota gagal; balasan dibersihkan dari markdown.
- 20 skenario evaluasi + pemeriksa otomatis (`eval/`).

## App (Android)
- **Tahun lahir saja**: layar usia setelah PIN/unlock (`AgeGateScreen`), disimpan terenkripsi; backend hanya menerima kelompok. Batas dihitung dengan kemungkinan umur TERMUDA (±1 tahun),
  jadi yang ragu masuk kelompok lebih protektif. CHILD tidak bisa coba ulang sampai tahun kalender berganti; kelompok dihitung ulang tiap buka app (otomatis naik kelas).
- **Matriks fitur** (`AgePolicy`, dites): TEEN = chat+terjemahan hanya dengan persetujuan orang tua/wali; kamera ke cloud, wajah, baca notifikasi, voice personalization, konteks memori ke cloud = tidak tersedia. CHILD/belum diisi = semua ditolak.
- Ditegakkan di `PrivacyRepository` (toggle ditolak + dimatikan otomatis bila kelompok berubah), `XnaiRepository`, `TranslationRepository`, `VisionRepository`, `FaceEngine`, dan listener notifikasi.
- Menu Privacy Control: "Usia & persetujuan orang tua"; kebijakan privasi in-app diperbarui; "hapus semua data" menghapus tahun lahir + persetujuan (penanda kunci anak-anak dipertahankan sampai tahun berganti).

## Batas yang harus kamu tahu
1. **Persetujuan orang tua = konfirmasi di perangkat, bukan verifikasi.** Komdigi menyebut platform tidak boleh sekadar mengandalkan centang usia dan wajib punya fitur persetujuan orang tua lewat notifikasi ke ortu/wali. Mekanisme ini kemungkinan BELUM cukup untuk 13-17. Pilihan A (18+ dulu) yang kusarankan masih paling aman; ini bukan nasihat hukum, cek ke Komdigi.
2. Usia hanya pernyataan diri dan hanya tahun: anak yang baru 13/18 bisa tertahan di kelompok bawah sampai tahun depan.
3. **Gaya Gen-Z belum dinilai dengan model asli.** Jalankan `npm run eval`, baca balasannya, sesuaikan `persona.mjs`.
4. Deteksi krisis berbasis kata kunci (meleset di ungkapan tersirat; kadang salah tangkap). Verifikasi nomor 112 sebelum rilis.
5. Kotlin baru (±600 baris) belum dikompilasi; hanya pemeriksa statis + `production-scan` PASS. Jalankan Actions dan kirim lognya bila merah.
6. Belum dibuat dari dokumen jiwa (disengaja/ditunda): sapaan proaktif & kebijakan inisiatif, mic selalu aktif, baca emosi dari suara, memori yang "tumbuh", Time Capsule, nimbrung sosial, Reverse Care/"kangen"/pacar (dibuang), verifikasi suara (diganti PIN).
7. `backendBaseUrl` di `xnai-config.json` masih kosong: backend belum di-deploy.

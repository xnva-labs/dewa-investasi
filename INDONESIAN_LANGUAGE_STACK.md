# XNAI Maximum Indonesian Language Stack

## Tujuan

XNAI diarahkan untuk memperoleh cakupan bahasa Indonesia seluas mungkin dari sumber yang tersedia secara sah. "Seluruh bahasa Indonesia" diperlakukan sebagai target cakupan yang terus berkembang, bukan klaim bahwa satu dump statis sudah berisi setiap kata, makna, dialek, istilah, atau pemakaian yang pernah ada.

## Lapisan bahasa

1. **Kamus resmi**: KBBI VI Daring sebagai referensi otoritatif. Data lengkap hanya boleh dilokalkan setelah ada izin/lisensi/export resmi.
2. **Kamus mesin**: Kaikki/Indonesian Wiktionary untuk lemma, bentuk, makna, dan relasi.
3. **Ejaan**: Hunspell Indonesian untuk daftar bentuk dan aturan afiks.
4. **Morfologi**: MALINDO Morph dan Apertium-Indonesian untuk akar, imbuhan, reduplikasi, dan analisis bentuk.
5. **Semantik**: Wordnet Bahasa untuk synset dan hubungan leksikal.
6. **Konteks**: UD Indonesian-GSD, TALPCo, Tatoeba, serta corpus lain yang lulus pemeriksaan lisensi.
7. **Terjemahan**: Apertium Indonesian-English dan korpus paralel sebagai relation `translation`, bukan sebagai definisi KBBI.
8. **Entitas/lexeme**: Wikidata untuk ID dan relasi terstruktur.
9. **Pengetahuan hidup**: Wikipedia Bahasa Indonesia dan Korpus Indonesia sebagai sumber riset eksternal sesuai ketentuan layanan.
10. **Speech**: Mozilla Common Voice Indonesian sebagai target dataset eksternal besar, tidak dibundel APK secara default.
11. **Benchmark/model**: IndoLEM/IndoBERT dan sumber NLP lain untuk evaluasi dan desain pipeline, bukan otomatis sebagai kamus.
12. **Korpus besar**: OPUS, web corpus, dan aggregator dataset hanya masuk per-corpus/per-sumber setelah lisensinya diverifikasi.
13. **Kandidat baru**: slang, istilah teknis, nama diri, bentuk regional, dan kata yang ditemukan XNAI dapat disimpan sebagai `candidate` sampai mendapat evidence/review.

## Aturan ID XNAI

Setiap bentuk leksikal baru memperoleh integer sederhana berikutnya secara otomatis: `1, 2, 3, ...`. ID yang sudah diberikan tidak pernah direnumber.

Sumber berbeda yang menemukan kata yang sama digabung pada satu lexical ID dengan banyak provenance. Senses, morphology, examples, translations, dan evidence menjadi record terpisah yang menunjuk ke lexical ID tersebut.

## Jangan campur status

- `official`: dikonfirmasi oleh sumber resmi.
- `source-attested`: ada pada sumber terbuka/berlisensi.
- `candidate`: ditemukan dari korpus/pemakaian tetapi belum tervalidasi sebagai lema baku.
- `reviewed`: telah ditinjau menurut aturan proyek.
- `conflicting`: sumber berbeda memberi informasi berbeda.
- `rejected`: bukti atau kualitas tidak memenuhi ambang.

Frekuensi kata tidak otomatis menjadikannya kata KBBI.

## Akuisisi

Tombol **Masukkan semua sumber terbuka** menjalankan sumber yang memiliki parser dan kebijakan import yang jelas, satu per satu, melalui streaming. Sumber eksternal atau berizin khusus tetap ditampilkan agar XNAI memiliki peta akuisisi tanpa menyalin data secara ilegal.

Data besar tidak masuk ke APK sebagai asset wajib. Ia disimpan di storage data XNAI dan dapat dihapus/diperbarui berdasarkan manifest serta checksum sumber.

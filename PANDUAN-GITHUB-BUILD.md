# PANDUAN-GITHUB-BUILD — API key jalan otomatis di APK hasil GitHub

## 1. Peta penempatan file di repo GitHub

```
repo/
├── .github/workflows/build-apk.yml
├── .gitignore
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        └── java/com/xnvalabs/investmenttracker/
            ├── MainActivity.kt
            ├── model/                  (Models.kt)
            ├── data/                   (AssetRepository, MockData, DictionaryData)
            │   ├── local/              (AssetEntity, AssetDao, AppDatabase, Converters)
            │   └── remote/             (BinanceApi, CoinGeckoApi, FinnhubApi,
            │                            ExchangeRateApi, GoogleNewsRss, GeminiApi,
            │                            AiAnalysisRepository, MarketDataRepository,
            │                            NetworkModule)
            ├── notification/           (NotificationHelper)
            ├── viewmodel/              (PortfolioViewModel, NewsAnalysisViewModel)
            ├── worker/                 (PriceSyncWorker)
            └── ui/                     (Theme, AppRoot, PortfolioScreen,
                                         SentimentScreen, ValuationScreen, DictionaryScreen)
```

## 2. Pasang API key sebagai GitHub Secret

1. Buka repo di GitHub -> Settings -> Secrets and variables -> Actions
2. New repository secret, buat DUA secret:

| Name | Isi | Dipakai untuk |
|------|-----|----------------|
| `FINNHUB_API_KEY` | key dari finnhub.io | Harga live saham/ETF US |
| `GEMINI_API_KEY` | key dari aistudio.google.com | Fitur "Tanya AI" (analisis berita) |

Key TIDAK ditulis di kode. Saat build, workflow mengoper secret ke Gradle,
masuk ke BuildConfig.FINNHUB_API_KEY / BuildConfig.GEMINI_API_KEY.

## 3. Build

Push ke branch main -> tab Actions -> "Build APK" -> tunggu hijau ->
download artifact "xnva-investment-debug-apk" -> install di HP.

## 4. Sumber data yang aktif di app

| Fitur | Sumber | Butuh key? |
|---|---|---|
| Harga live Crypto | Binance -> CoinGecko | Tidak |
| Harga live Saham/ETF US | Finnhub | Ya (FINNHUB_API_KEY) |
| Kurs USD/IDR | Binance (USDTIDR) -> open.er-api.com | Tidak |
| Saham IDX | belum ada | harga diisi manual |
| Tanya AI (tab Sentiment) | Google News RSS (gratis, tanpa key) + Gemini 2.5 Flash-Lite | Ya (GEMINI_API_KEY) |

Fitur "Tanya AI": cari berita 3 hari terakhir (ID + global sekaligus) soal
satu saham/ticker, lalu Gemini merumuskan faktor pendukung naik/turun HANYA
dari berita itu -- bukan prediksi harga pasti, selalu ada sitasi sumber
beritanya dan disclaimer di jawabannya.

## 5. Tes key dulu sebelum build (Termux)

Finnhub:

    curl "https://finnhub.io/api/v1/quote?symbol=AAPL&token=KEY_KAMU"

Key benar: `{"c":...}` dengan c lebih dari 0. Key salah: `{"error":"Invalid API key"}`.
Dashboard Finnhub ada dua label mirip -- pakai "API Key", BUKAN "Webhook Secret".

Gemini:

    curl -X POST "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent" \
      -H "x-goog-api-key: KEY_KAMU" -H "Content-Type: application/json" \
      -d '{"contents":[{"parts":[{"text":"halo"}]}]}'

Key benar: balik JSON berisi `candidates`. Key salah: balik error 400/403.

## 6. Keamanan

- Key di dalam APK bisa diekstrak siapa pun yang memegang APK-nya. Untuk pemakaian
  pribadi dengan key gratis ini risikonya kecil, tapi JANGAN bagikan APK-nya.
- Pakai repo PRIVATE. Di repo publik, artifact APK bisa diunduh user GitHub lain.
- Kalau key bocor (mis. sempat ditempel di chat/tempat publik): masuk dashboard
  provider-nya (Finnhub / Google AI Studio) -> generate ulang -> update GitHub Secret.
- Jangan tempel key di chat/kode/README.

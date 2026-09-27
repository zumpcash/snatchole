# UMBRA — Kurulum Kılavuzu

## 1. Projeyi Aç
Android Studio → File → Open → `umbra-android/` klasörünü seç.

## 2. Treasury Adresini Gir
`WalletViewModel.kt` dosyasında aşağıdaki satırı bul ve kendi Solana adresini yaz:
```kotlin
const val TREASURY_ADDRESS = "YOUR_SOLANA_TREASURY_ADDRESS_HERE"
```

## 3. Sync & Build
- Android Studio otomatik Gradle sync yapar.
- Eğer hata çıkarsa: File → Invalidate Caches → Restart

## 4. Emülatör veya Fiziksel Cihaz
- Seeker için USB Debug aç, cihaza yükle.
- `Run` (▶) tuşuna bas.

## Platform Destek Durumu
| Platform   | Yöntem                          | Maliyet |
|------------|--------------------------------|---------|
| YouTube    | Innertube ANDROID client       | $0      |
| TikTok     | tikwm.com API + HTML fallback  | $0      |
| Instagram  | Embed page + GraphQL backup    | $0      |
| X/Twitter  | Syndication API                | $0      |

## Solana Ödeme Akışı
- SOL transferi cihazda inşa edilir, Phantom/Seed Vault imzalar.
- Ödeme doğrulama: RPC'de işlem taranır, lokalde DataStore'a kaydedilir.
- Restore: Son 24 işlem taranır, geçerli ödeme bulunursa premium aktif olur.

## Notlar
- `minSdk 26` (Android 8) — Seeker Android 13 çalıştırıyor.
- İndirmeler `Movies/UMBRA/` klasörüne kaydedilir.
- Bildirim izni Android 13+ için otomatik istenir.

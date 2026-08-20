# Değişiklikler

## 1.0.1 — kimlik doğrulama (1.0.0 bunsuz yayınlandı)

- `Bildirim.login(externalId, identityHash = imza)`. **1.0.0 Maven Central'a bu olmadan çıktı**
  (kimlik doğrulama commit'i o an itilmemişti); Central kalıcı olduğu için düzeltme bu sürümde.
  Dokümandaki `login(externalId, identityHash)` imzası 1.0.1 ile çalışır.
- Yayın betiği artık kirli ağaç / itilmemiş commit varsa duruyor.
- Sürüm testi eşitlik yerine "sözleşmedeki `latest`in gerisinde olmama" kuralına geçti;
  bump ile yayın arasındaki pencere artık depoyu kırmızıya düşürmüyor.

## 1.0.0 — Maven Central'da (2026-08-18)

- İlk sürüm: kurulum, izin akışı (Android 13+), jeton + kurulum kimliği, `login/logout/setTags/track`,
  data-only bildirim çizimi (görsel, ikon, en çok 3 aksiyon düğmesi), gösterim/tıklama/kapatma
  ölçümü, deep link açma, çevrimdışı sıralı kuyruk, 24 saatlik kayıt tazeleme.
- Genel API `bildirim.io/dokumanlar/android` ile birebir: `initialize(context, appKey[, config])`,
  `requestPermission`, `login/logout/setTags/track/unsubscribe/subscribe`,
  `setNotificationOpenedHandler`/`setForegroundHandler` (bildirim nesnesi: `campaignId, url,
  actionId, title, body, image, icon`), kendi servisi olanlar için `onNewToken`/`onMessageReceived`,
  servis adı `io.bildirim.sdk.internal.MessagingService`.
- Enstrümantasyon testleri (`connectedDebugAndroidTest`) ve CI (birim + sözleşme + emülatör).
- Yayın (Faz E) hazır: `maven-publish` + koşullu `signing`, sources + javadoc jar (Central
  zorunlu), `scripts/release-android.sh` Central Portal bundle akışı — imza hattı test
  anahtarıyla doğrulandı (5 `.asc`).
- **Kimlik doğrulama**: `Bildirim.login(externalId, identityHash = imza)` — imza
  `HMAC-SHA256(proje kimlik sırrı, externalId)` (hex), müşterinin sunucusu üretir; `/v1/subscribe`
  gövdesine `identityHash` eklendi, `403 identity_verification_required` yol gösteren mesajla
  raporlanıyor ve kuyruğu tıkamıyor.
- `scripts/check-contract.sh` artık **içeriği** karşılaştırıyor: sunucu sürümü 3'te tutup gövdeye
  alan eklediğinde yalnız sürüme bakan kontrol bunu kaçırmıştı.
- Enstrümantasyon testindeki yarış giderildi (`notify()` asenkron; artık bekleniyor).
- Sözleşme sürümü 3.

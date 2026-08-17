# Değişiklikler

## 1.0.0 — yayına hazır (Maven Central etiketi bekliyor)

- İlk sürüm: kurulum, izin akışı (Android 13+), jeton + kurulum kimliği, `login/logout/setTags/track`,
  data-only bildirim çizimi (görsel, ikon, en çok 3 aksiyon düğmesi), gösterim/tıklama/kapatma
  ölçümü, deep link açma, çevrimdışı sıralı kuyruk, 24 saatlik kayıt tazeleme.
- Genel API `bildirim.io/dokumanlar/android` ile birebir: `initialize(context, appKey[, config])`,
  `requestPermission`, `login/logout/setTags/track/unsubscribe/subscribe`,
  `setNotificationOpenedHandler`/`setForegroundHandler` (bildirim nesnesi: `campaignId, url,
  actionId, title, body, image, icon`), kendi servisi olanlar için `onNewToken`/`onMessageReceived`,
  servis adı `io.bildirim.sdk.internal.MessagingService`.
- Sözleşme sürümü 3.

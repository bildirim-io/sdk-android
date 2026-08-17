# Bildirim Android SDK

[bildirim.io](https://bildirim.io) için Android push bildirim SDK'sı. Tek bağımlılıkla eklenir,
jeton yaşam döngüsünü, izin akışını, bildirim çizimini, tıklama/gösterim ölçümünü ve
çevrimdışı kuyruğu üstlenir. Firebase projeniz **sizin** kalır: SDK kendi Firebase hesabını
dayatmaz, panele girdiğiniz service account ile gönderim yapılır.

- Min SDK 21, hedef 36. Bağımlılık: yalnız `firebase-messaging` (sürümünü siz seçersiniz).
- androidx / OkHttp / coroutines **yok** — uygulamanızın sürümleriyle çakışmaz.
- Reklam kimliği (AAID) **toplanmaz**. Toplananlar: cihaz jetonu, SDK'nın ürettiği kurulum
  kimliği, uygulama/SDK sürümü, saat dilimi, ülke kodu (bkz. `DATA-SAFETY.md`).

## Kurulum

1. Firebase Console'da uygulamanız için `google-services.json` alın; Panel → Ayarlar → Mobil
   Push'a **service account JSON**'unu yükleyip **Doğrula**'ya basın (doğrulanmadan kayıt 403 döner).
2. Uygulama modülü:

```kotlin
dependencies {
    implementation("io.bildirim:bildirim-android:1.0.0")
    implementation("com.google.firebase:firebase-messaging:24.0.0") // sizde zaten varsa dokunmayın
}
```

3. `Application.onCreate`:

```kotlin
class UygulamaniZ : Application() {
    override fun onCreate() {
        super.onCreate()
        Bildirim.initialize(this, "pk_sizin_anahtariniz")   // Panel → Ayarlar → API anahtarları
    }
}
```

4. Uygun bir yerde izin isteyin (Android 13+ sistem diyaloğu; altında zaten açık):

```kotlin
Bildirim.requestPermission(this) { granted -> }
```

Bu kadar. Cihaz panelde **Aboneler**'de görünür; **Kampanya → Test cihazıma gönder** ile
uçtan uca deneyin.

## API

```kotlin
Bildirim.initialize(this, "pk_...", BildirimConfig(
    apiBase = "https://api.sirketiniz.com",   // kendi sunucunuz
    channelName = "Son dakika",
    smallIcon = R.drawable.ic_stat_bildirim,   // tek renkli, saydam arka plan
    accentColor = R.color.marka,               // renk KAYNAĞI
))
Bildirim.requestPermission { granted -> }
Bildirim.login("kullanici-42");  Bildirim.logout()
Bildirim.setTags(mapOf("sehir" to "istanbul", "plan" to null))   // null → siler
Bildirim.track("satin_alma", mapOf("value" to 149.9, "currency" to "TRY"))
Bildirim.unsubscribe();  Bildirim.subscribe()
Bildirim.setNotificationOpenedHandler { bildirim -> /* bildirim.url, .campaignId, .actionId */ true }
Bildirim.setForegroundHandler { bildirim -> true }  // true → SDK çizsin
Bildirim.getToken(); Bildirim.getInstallationId(); Bildirim.getExternalId(); Bildirim.getTags()
```

`setNotificationOpenedHandler` `false` dönerse SDK adresi açar: uygulamanız `url`'i işliyorsa
(deep link / app link) uygulama, https ise tarayıcı, yoksa ana ekran (`Intent` extra
`io.bildirim.sdk.url`). `true` dönerseniz yönlendirmeyi siz yapmışsınızdır.

Bildirim nesnesi: `campaignId, url, actionId, title, body, image, icon` (+ `actions`).

## Nasıl çalışır

- **Data-only mesaj**: sunucu `notification` bloğu göndermez, SDK çizer — böylece "gösterildi"
  ölçülür, görsel ve aksiyon düğmeleri her durumda çalışır. Kanal `bildirim_default`
  (Önem: Yüksek); kullanıcı ayarlardan değiştirebilir.
- **Kayıt disiplini**: `/v1/subscribe` yalnız ilk kayıt, jeton değişimi, `login/logout/setTags`
  ve 24 saatte bir tazeleme için çağrılır. Her açılışta değil (CGNAT + oran sınırı).
- **Kurulum kimliği**: SDK'nın ürettiği UUID; jeton yenilendiğinde sunucu eski kaydı taşır,
  aynı cihaz iki abone sayılmaz.
- **Çevrimdışı kuyruk**: `login/setTags/track` ve olaylar sırayla saklanır, ağ gelince
  aynı sırayla gider (`logout` sonrası eski `setTags` yeni kullanıcıya yazılmaz).
- **Tıklama**: Android 12+ trambolin kısıtına uygun görünmez Activity üzerinden; aksiyon
  düğmesi tıklaması `actionId` ile raporlanır.

## Kendi FirebaseMessagingService'iniz varsa

`docs/KENDI-SERVISINIZ-VARSA.md` — iki satır: `onNewToken` → `Bildirim.onNewToken(token)`,
`onMessageReceived` → `if (Bildirim.onMessageReceived(message)) return`; ardından SDK'nın servisini
manifest'ten çıkarın (`io.bildirim.sdk.internal.MessagingService`, `tools:node="remove"`).

## Geliştirme

```bash
./gradlew :bildirim:assembleRelease :bildirim:testDebugUnitTest   # kütüphane + birim testleri
./gradlew :ornek-uygulama:installDebug                            # google-services.json gerekir
scripts/check-contract.sh                                          # vendored sözleşme == canlı
```

Sözleşme: `contracts/mobile-sdk.json` (ana depodan kopya; `GET /v1/mobile/contract` aynısını
döner). `ContractTest` koddaki sabitleri dosyayla karşılaştırır; **`DocSurfaceTest`** yayındaki
`bildirim.io/dokumanlar/android` sayfasındaki kod bloklarını birebir derler — genel API'yi
değiştirmek için önce dokümanı (ve ana depodaki `test/mobile-s3.mjs`'i) güncellemek gerekir.

Lisans: MIT.

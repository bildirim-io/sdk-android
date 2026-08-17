# Bildirim Android SDK

[bildirim.io](https://bildirim.io) için Android push bildirim SDK'sı. Tek bağımlılıkla eklenir,
jeton yaşam döngüsünü, izin akışını, bildirim çizimini, tıklama/gösterim ölçümünü ve
çevrimdışı kuyruğu üstlenir. Firebase projeniz **sizin** kalır: SDK kendi Firebase hesabını
dayatmaz, panele girdiğiniz service account ile gönderim yapılır.

- Min SDK 21, hedef 36. Bağımlılık: yalnız `firebase-messaging` (sürümünü siz seçersiniz).
- androidx / OkHttp / coroutines **yok** — uygulamanızın sürümleriyle çakışmaz.
- Reklam kimliği (AAID) **toplanmaz**. Toplananlar: cihaz jetonu, SDK'nın ürettiği kurulum
  kimliği, uygulama/SDK sürümü, saat dilimi, ülke kodu (bkz. `docs/PLAY-DATA-SAFETY.md`).

## Kurulum

1. Firebase Console'da uygulamanız için `google-services.json` alın; Panel → Ayarlar → Mobil
   Push'a **service account JSON**'unu yükleyip **Doğrula**'ya basın.
2. `settings.gradle.kts` → `google()` ve `mavenCentral()` zaten vardır. Uygulama modülü:

```kotlin
plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
}
dependencies {
    implementation("io.bildirim:bildirim-android:0.1.0")
    implementation(platform("com.google.firebase:firebase-bom:33.13.0"))
    implementation("com.google.firebase:firebase-messaging")
}
```

3. `Application.onCreate`:

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Bildirim.initialize(this, "pk_...")   // Panel → Ayarlar → Anahtarlar
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
Bildirim.initialize(context, BildirimConfig(appKey = "pk_...", apiBase = "https://api.bildirim.io",
    channelId = "bildirim_default", channelName = "Bildirimler", smallIconRes = R.drawable.ic_stat, accentColor = 0xFF0055FF.toInt()))
Bildirim.requestPermission(activity) { granted -> }
Bildirim.login("kullanici-42");  Bildirim.logout()
Bildirim.setTags(mapOf("sehir" to "istanbul", "eski" to null))   // null → siler
Bildirim.track("satin_alma", value = 149.9, currency = "TRY", properties = mapOf("urun" to "x"))
Bildirim.unsubscribe();  Bildirim.subscribe()
Bildirim.setNotificationOpenedHandler { result -> /* result.notification, result.actionId, result.url */ false }
Bildirim.setForegroundHandler { notification -> true }  // true → SDK çizsin
Bildirim.getToken(); Bildirim.getInstallationId(); Bildirim.getExternalId(); Bildirim.getTags()
```

`setNotificationOpenedHandler` `false` dönerse SDK adresi açar: uygulamanız `url`'i işliyorsa
(deep link / app link) uygulama, https ise tarayıcı, yoksa ana ekran (`Intent` extra
`io.bildirim.sdk.url`). `true` dönerseniz yönlendirmeyi siz yapmışsınızdır.

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

`docs/KENDI-SERVISINIZ-VARSA.md` — iki satır: `onNewToken` → `Bildirim.setToken(this, token)`,
`onMessageReceived` → `if (Bildirim.handleRemoteMessage(this, message.data)) return`.

## Geliştirme

```bash
./gradlew :bildirim:assembleRelease :bildirim:testDebugUnitTest   # kütüphane + birim testleri
./gradlew :ornek-uygulama:installDebug                            # google-services.json gerekir
scripts/check-contract.sh                                          # vendored sözleşme == canlı
```

Sözleşme: `contracts/mobile-sdk.json` (ana depodan kopya; `GET /v1/mobile/contract` aynısını
döner). `ContractTest` koddaki sabitleri dosyayla karşılaştırır.

Lisans: MIT.

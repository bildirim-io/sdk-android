# Kendi FirebaseMessagingService'iniz varsa

Android tek bir `FirebaseMessagingService` seçer. SDK'nın servisi manifestte
`android:priority="-1"` ile kayıtlıdır; sizinki (varsayılan öncelik 0) varsa sistem
**sizinkini** çağırır ve SDK hiçbir mesaj görmez. İki satır ekleyin:

```kotlin
class BenimServisim : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        Bildirim.onNewToken(token)
        // kendi işleriniz…
    }
    override fun onMessageReceived(message: RemoteMessage) {
        if (Bildirim.onMessageReceived(message)) return   // Bildirim'den geldi, SDK çizdi
        // kendi mesajlarınız…
    }
}
```

`onMessageReceived` yalnız `data.bildirim` taşıyan mesajları üstlenir ve `true` döner; diğerlerine dokunmaz.

İsterseniz SDK'nın servisini tamamen kaldırabilirsiniz:

```xml
<service android:name="io.bildirim.sdk.internal.MessagingService" tools:node="remove" />
```

# Kendi FirebaseMessagingService'iniz varsa

Android tek bir `FirebaseMessagingService` seçer. SDK'nın servisi manifestte
`android:priority="-1"` ile kayıtlıdır; sizinki (varsayılan öncelik 0) varsa sistem
**sizinkini** çağırır ve SDK hiçbir mesaj görmez. İki satır ekleyin:

```kotlin
class MyMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        Bildirim.setToken(this, token)
        // kendi işleriniz…
    }
    override fun onMessageReceived(message: RemoteMessage) {
        if (Bildirim.handleRemoteMessage(this, message.data)) return   // Bildirim'e aitse SDK işledi
        // kendi mesajlarınız…
    }
}
```

`handleRemoteMessage` mesaj Bildirim'den değilse (`data.bildirim` yoksa) dokunmaz ve `false` döner.

İsterseniz SDK'nın servisini tamamen kaldırabilirsiniz:

```xml
<service android:name="io.bildirim.sdk.internal.BildirimMessagingService" tools:node="remove" />
```

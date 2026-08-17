package io.bildirim.sdk.internal

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.bildirim.sdk.Bildirim

/**
 * Yalnız delegasyon — mantık Bildirim.setToken / Bildirim.handleRemoteMessage'ta. Müşterinin
 * kendi servisi varsa aynı iki çağrıyı oradan yapar (docs/KENDI-SERVISINIZ-VARSA.md).
 */
internal class BildirimMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Bildirim.setToken(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Bildirim.handleRemoteMessage(applicationContext, message.data)
    }
}

package io.bildirim.sdk.internal

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.bildirim.sdk.Bildirim

/**
 * SDK'nın FCM servisi — kütüphane manifestinden birleşir, müşteri bir şey eklemez. Mantık
 * `Bildirim.onNewToken` / `Bildirim.onMessageReceived`'da; müşterinin kendi servisi varsa aynı iki
 * çağrıyı oradan yapar ve bu servisi manifest'ten çıkarır:
 *
 * ```xml
 * <service android:name="io.bildirim.sdk.internal.MessagingService" tools:node="remove" />
 * ```
 */
internal class MessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Bildirim.internalSetToken(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Bildirim.internalHandleData(applicationContext, message.data)
    }
}

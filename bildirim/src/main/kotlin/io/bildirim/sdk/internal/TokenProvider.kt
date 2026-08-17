package io.bildirim.sdk.internal

import io.bildirim.sdk.TokenProvider

/**
 * FirebaseMessaging.getInstance().token — Class.forName ile korunur: Firebase yoksa ya da
 * FirebaseApp başlatılmamışsa (google-services eksik) SDK çökmez, tek satır hata loglar.
 */
internal object FirebaseTokenProvider : TokenProvider {
    override fun fetch(callback: (String?) -> Unit) {
        try {
            Class.forName("com.google.firebase.messaging.FirebaseMessaging")
            val fm = com.google.firebase.messaging.FirebaseMessaging.getInstance()
            fm.token
                .addOnSuccessListener { callback(it) }
                .addOnFailureListener {
                    Log.e("FCM jetonu alınamadı: ${it.message}")
                    callback(null)
                }
        } catch (e: ClassNotFoundException) {
            Log.e("firebase-messaging bulunamadı. Uygulamaya com.google.firebase:firebase-messaging ekleyin.")
            callback(null)
        } catch (e: NoClassDefFoundError) {
            Log.e("firebase-messaging bulunamadı. Uygulamaya com.google.firebase:firebase-messaging ekleyin.")
            callback(null)
        } catch (e: IllegalStateException) {
            Log.e("FirebaseApp başlatılmamış (google-services.json eksik olabilir): ${e.message}")
            callback(null)
        } catch (e: Exception) {
            Log.e("FCM jetonu alınırken hata: ${e.message}")
            callback(null)
        }
    }
}

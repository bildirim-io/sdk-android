package io.bildirim.ornek

import android.app.Application
import android.util.Log
import io.bildirim.sdk.Bildirim
import io.bildirim.sdk.BildirimConfig

class OrnekApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Panel → Ayarlar → Anahtarlar'daki pk_ anahtarınızı buraya yazın.
        Bildirim.initialize(
            this,
            BildirimConfig(
                appKey = APP_KEY,
                logLevel = BildirimConfig.LOG_DEBUG,
            ),
        )
        Bildirim.setNotificationOpenedHandler { result ->
            Log.i("Ornek", "bildirim açıldı: kampanya=${result.notification.campaignId} aksiyon=${result.actionId} url=${result.url}")
            false // false → SDK adresi açar (deep link → MainActivity, https → tarayıcı)
        }
        Bildirim.setForegroundHandler { n ->
            Log.i("Ornek", "ön planda bildirim: ${n.title}")
            true // true → SDK çizsin
        }
    }

    companion object {
        const val APP_KEY = "pk_BURAYA_ANAHTARINIZI_YAZIN"
    }
}

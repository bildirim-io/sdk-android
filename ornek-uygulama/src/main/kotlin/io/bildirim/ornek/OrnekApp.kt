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
            APP_KEY,
            BildirimConfig(
                channelName = "Örnek bildirimler",
                smallIcon = android.R.drawable.ic_dialog_email,
                logLevel = BildirimConfig.LOG_DEBUG,
            ),
        )
        Bildirim.setNotificationOpenedHandler { bildirim ->
            Log.i("Ornek", "bildirim açıldı: kampanya=${bildirim.campaignId} aksiyon=${bildirim.actionId} url=${bildirim.url}")
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

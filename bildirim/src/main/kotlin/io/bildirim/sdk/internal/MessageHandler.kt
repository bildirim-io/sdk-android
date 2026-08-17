package io.bildirim.sdk.internal

import android.os.Handler
import android.os.Looper
import io.bildirim.sdk.BildirimNotification
import io.bildirim.sdk.ForegroundHandler
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Gelen data-only mesajı işler: çözümle → (ön plandaysa handler'a sor) → çiz → "gösterildi".
 * FCM'nin onMessageReceived bütçesi ~10 sn: metin hemen çizilir, görsel için ≤8 sn beklenir.
 */
internal class MessageHandler(
    private val renderer: NotificationRenderer,
    private val sync: SyncEngine,
    private val isForeground: () -> Boolean,
    private val foregroundHandler: () -> ForegroundHandler?,
    private val showInForeground: Boolean,
) {
    fun handle(n: BildirimNotification): Boolean {
        if (isForeground()) {
            val handler = foregroundHandler()
            val show = if (handler != null) askForeground(handler, n) else showInForeground
            if (!show) {
                Log.d("ön planda, uygulama çizmeyi üstlendi: ${n.campaignId}")
                return true
            }
        }
        val icon = n.iconUrl?.let { ImageLoader.load(it, 3_000) }
        val image = n.imageUrl?.let { ImageLoader.load(it, 8_000) }
        renderer.show(n, image, icon)
        if (sync.notificationsEnabled()) {
            n.eventToken?.let { sync.enqueueEvent(it, Contract.EV_DISPLAYED, null) }
        } else {
            Log.d("bildirim izni yok; gösterim sayılmadı")
        }
        return true
    }

    private fun askForeground(handler: ForegroundHandler, n: BildirimNotification): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return try { handler.onForeground(n) } catch (e: Exception) { Log.w("foreground handler hata: ${e.message}"); true }
        }
        val latch = CountDownLatch(1)
        var result = true
        Handler(Looper.getMainLooper()).post {
            try { result = handler.onForeground(n) } catch (e: Exception) { Log.w("foreground handler hata: ${e.message}") }
            latch.countDown()
        }
        if (!latch.await(1_500, TimeUnit.MILLISECONDS)) Log.w("foreground handler 1.5 sn içinde dönmedi; bildirim çiziliyor")
        return result
    }
}

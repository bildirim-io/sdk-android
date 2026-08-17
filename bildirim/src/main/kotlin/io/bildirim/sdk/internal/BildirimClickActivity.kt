package io.bildirim.sdk.internal

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import io.bildirim.sdk.Bildirim
import io.bildirim.sdk.BildirimOpenResult

/**
 * Bildirim tıklamalarının tek giriş noktası (gövde ve aksiyon düğmeleri). Görünmez
 * (Theme.NoDisplay → onCreate içinde finish şart). Sıra: olayı kuyruğa al → bildirimi kapat →
 * opened handler → handler üstlenmediyse adresi aç.
 */
internal class BildirimClickActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            handle(intent)
        } catch (e: Exception) {
            Log.e("tıklama işlenemedi: ${e.message}", e)
        } finally {
            finish()
        }
    }

    private fun handle(intent: Intent?) {
        val raw = intent?.getStringExtra(EXTRA_RAW) ?: return
        val n = Payload.parseRaw(raw) ?: return
        val actionId = intent.getStringExtra(EXTRA_ACTION_ID)
        val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, n.notificationId)

        Bildirim.internalReportEvent(this, n, Contract.EV_CLICKED, actionId)
        Bildirim.internalCancelNotification(this, notifId)

        val actionUrl = actionId?.let { id -> n.actions.firstOrNull { it.id == id }?.url }
        val url = actionUrl ?: n.url
        val result = BildirimOpenResult(n, actionId, url)

        val handled = Bildirim.internalDispatchOpened(result)
        if (!handled) openDefault(url)
    }

    private fun openDefault(url: String?) {
        val pkg = packageName
        if (url != null) {
            val uri = try { Uri.parse(url) } catch (_: Exception) { null }
            if (uri != null) {
                // 1) Uygulamanın kendisi bu adresi işliyor mu (deep link / app link)?
                val own = Intent(Intent.ACTION_VIEW, uri).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (own.resolveActivity(packageManager) != null) {
                    startActivity(own); return
                }
                // 2) Dış hedef (tarayıcı, başka uygulama)
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return
                } catch (_: ActivityNotFoundException) {
                    Log.w("adres açılamadı: $url")
                }
            }
        }
        // 3) Uygulamanın ana ekranı
        val launch = packageManager.getLaunchIntentForPackage(pkg) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        url?.let { launch.putExtra(EXTRA_URL, it) }
        startActivity(launch)
    }

    companion object {
        const val ACTION = "io.bildirim.sdk.CLICK"
        const val EXTRA_RAW = "io.bildirim.sdk.raw"
        const val EXTRA_ACTION_ID = "io.bildirim.sdk.actionId"
        const val EXTRA_NOTIFICATION_ID = "io.bildirim.sdk.notificationId"
        /** Ana ekran açılırken bildirimin adresi bu extra ile iletilir. */
        const val EXTRA_URL = "io.bildirim.sdk.url"
    }
}

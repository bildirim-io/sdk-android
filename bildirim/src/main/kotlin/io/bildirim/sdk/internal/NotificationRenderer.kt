package io.bildirim.sdk.internal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import io.bildirim.sdk.BildirimConfig
import io.bildirim.sdk.BildirimNotification

/**
 * Data-only FCM mesajından bildirim çizer. androidx (NotificationCompat) bilerek kullanılmaz —
 * kütüphane bağımlılığı müşteri için sürüm çakışması maliyetidir; platform Notification.Builder
 * API 21'den beri yeterli.
 *
 * İki fazlı: metin bildirimi hemen (gösterim ölçümü burada), görsel varsa indirilip aynı id ile
 * yeniden yayımlanır (`setOnlyAlertOnce` → ikinci kez ses/titreşim yok).
 */
internal class NotificationRenderer(private val context: Context, private val config: BildirimConfig) {

    private val nm: NotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val existing = nm.getNotificationChannel(config.channelId)
        if (existing != null) return
        val ch = NotificationChannel(config.channelId, config.channelName, NotificationManager.IMPORTANCE_HIGH)
        nm.createNotificationChannel(ch)
    }

    /** İlk faz: metin. Döner: bildirim id'si. */
    fun show(n: BildirimNotification, image: Bitmap? = null, icon: Bitmap? = null) {
        ensureChannel()
        val id = n.notificationId
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, config.channelId) else Notification.Builder(context)

        builder.setSmallIcon(smallIcon())
            .setContentTitle(n.title ?: appLabel())
            .setContentText(n.body ?: "")
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(clickIntent(n, id, null, 0))
            .setDeleteIntent(dismissIntent(n, id))
        accentColor()?.let { builder.setColor(it) }
        if (Build.VERSION.SDK_INT < 26) {
            builder.setPriority(Notification.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_ALL)
        }
        if (Build.VERSION.SDK_INT >= 21) builder.setVisibility(Notification.VISIBILITY_PUBLIC)

        if (icon != null) builder.setLargeIcon(icon)
        if (image != null) {
            val style = Notification.BigPictureStyle().bigPicture(image)
            n.body?.let { style.setSummaryText(it) }
            if (icon != null) style.bigLargeIcon(null as Bitmap?)
            builder.style = style
        } else if (n.body != null) {
            builder.style = Notification.BigTextStyle().bigText(n.body)
        }

        n.actions.take(Contract.MAX_ACTIONS).forEachIndexed { idx, a ->
            @Suppress("DEPRECATION")
            val action = Notification.Action.Builder(0, a.label, clickIntent(n, id, a.id, idx + 1)).build()
            builder.addAction(action)
        }

        nm.notify(TAG, id, builder.build())
    }

    fun cancel(id: Int) = nm.cancel(TAG, id)

    private fun clickIntent(n: BildirimNotification, id: Int, actionId: String?, index: Int): PendingIntent {
        val intent = Intent(context, BildirimClickActivity::class.java)
            .setAction(BildirimClickActivity.ACTION)
            // Ayırt edici data: aynı bildirimin farklı düğmeleri farklı PendingIntent olsun (extras eşitliğe girmez).
            .setData(Uri.parse("bildirim://n/$id/$index"))
            .putExtra(BildirimClickActivity.EXTRA_RAW, n.raw)
            .putExtra(BildirimClickActivity.EXTRA_NOTIFICATION_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        actionId?.let { intent.putExtra(BildirimClickActivity.EXTRA_ACTION_ID, it) }
        return PendingIntent.getActivity(context, id * 16 + index, intent, piFlags())
    }

    private fun dismissIntent(n: BildirimNotification, id: Int): PendingIntent {
        val intent = Intent(context, BildirimDismissReceiver::class.java)
            .setAction(BildirimDismissReceiver.ACTION)
            .setData(Uri.parse("bildirim://d/$id"))
            .putExtra(BildirimClickActivity.EXTRA_RAW, n.raw)
        return PendingIntent.getBroadcast(context, id, intent, piFlags())
    }

    private fun piFlags(): Int {
        var f = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= 23) f = f or PendingIntent.FLAG_IMMUTABLE
        return f
    }

    /** Vurgu rengi renk KAYNAĞI olarak verilir (doküman: `accentColor = R.color.marka`). */
    private fun accentColor(): Int? {
        if (config.accentColor == 0) return null
        return try {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT >= 23) context.resources.getColor(config.accentColor, context.theme)
            else context.resources.getColor(config.accentColor)
        } catch (e: Exception) {
            // Kaynak değil de doğrudan ARGB verilmişse onu kullan
            config.accentColor
        }
    }

    private fun smallIcon(): Int {
        if (config.smallIcon != 0) return config.smallIcon
        try {
            val ai = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            val res = ai.metaData?.getInt(META_SMALL_ICON, 0) ?: 0
            if (res != 0) return res
            if (ai.icon != 0) return ai.icon
        } catch (_: Exception) {}
        return android.R.drawable.ic_dialog_info
    }

    private fun appLabel(): CharSequence = try {
        context.packageManager.getApplicationLabel(context.applicationInfo)
    } catch (_: Exception) { "" }

    companion object {
        const val TAG = "bildirim"
        const val META_SMALL_ICON = "io.bildirim.sdk.small_icon"
    }
}

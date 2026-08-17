package io.bildirim.sdk.internal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.bildirim.sdk.Bildirim

/** deleteIntent hedefi: kullanıcı bildirimi kaydırarak sildi → "dismissed". */
internal class BildirimDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val raw = intent?.getStringExtra(BildirimClickActivity.EXTRA_RAW) ?: return
        val n = Payload.parseRaw(raw) ?: return
        Bildirim.internalReportEvent(context, n, Contract.EV_DISMISSED, null)
    }

    companion object {
        const val ACTION = "io.bildirim.sdk.DISMISS"
    }
}

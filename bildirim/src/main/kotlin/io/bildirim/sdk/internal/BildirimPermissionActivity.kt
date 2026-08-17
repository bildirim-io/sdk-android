package io.bildirim.sdk.internal

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle

/**
 * Android 13+ POST_NOTIFICATIONS iznini müşterinin Activity'sine dokunmadan ister
 * (onRequestPermissionsResult yönlendirmesi gerekmez). Saydam, geçmişte kalmaz.
 */
internal class BildirimPermissionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < 33) { deliver(true); finish(); return }
        if (checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED) { deliver(true); finish(); return }
        if (savedInstanceState == null) requestPermissions(arrayOf(PERMISSION), REQ)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        deliver(granted)
        finish()
    }

    private fun deliver(granted: Boolean) {
        val cb = pending
        pending = null
        cb?.invoke(granted)
    }

    companion object {
        const val PERMISSION = "android.permission.POST_NOTIFICATIONS"
        private const val REQ = 4131
        @Volatile private var pending: ((Boolean) -> Unit)? = null

        fun launch(context: Context, callback: (Boolean) -> Unit) {
            pending = callback
            context.startActivity(
                Intent(context, BildirimPermissionActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
        }
    }
}

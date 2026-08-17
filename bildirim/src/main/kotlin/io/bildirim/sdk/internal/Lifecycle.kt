package io.bildirim.sdk.internal

import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.Bundle
import java.util.concurrent.atomic.AtomicInteger

/**
 * Ön plan sayacı (foreground handler kararı için) + ön plana geçişte / ağ gelince kuyruk boşaltma.
 */
internal class Lifecycle(private val onForeground: () -> Unit, private val onNetwork: () -> Unit) :
    Application.ActivityLifecycleCallbacks {

    private val started = AtomicInteger(0)

    val isForeground: Boolean get() = started.get() > 0

    fun attach(context: Context) {
        (context.applicationContext as? Application)?.registerActivityLifecycleCallbacks(this)
        if (Build.VERSION.SDK_INT >= 24) {
            try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                cm?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) { onNetwork() }
                })
            } catch (e: Exception) {
                Log.d("ağ dinleyicisi kurulamadı: ${e.message}")
            }
        }
    }

    override fun onActivityStarted(activity: Activity) {
        if (activity is BildirimClickActivity || activity is BildirimPermissionActivity) return
        if (started.incrementAndGet() == 1) onForeground()
    }

    override fun onActivityStopped(activity: Activity) {
        if (activity is BildirimClickActivity || activity is BildirimPermissionActivity) return
        started.decrementAndGet()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}

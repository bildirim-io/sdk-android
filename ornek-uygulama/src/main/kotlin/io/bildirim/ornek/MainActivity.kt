package io.bildirim.ornek

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.bildirim.sdk.Bildirim

/**
 * SDK'nın her yeteneğini bir düğmeyle deneyen basit ekran. Panelde Aboneler ve kampanya
 * istatistiklerinden sonucu izleyin.
 */
class MainActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }
        status = TextView(this).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setPadding(0, 0, 0, 24) }
        root.addView(status)

        fun button(label: String, onClick: () -> Unit) {
            root.addView(Button(this).apply { text = label; setOnClickListener { onClick(); refresh() } })
        }
        button("Bildirim izni iste") { Bildirim.requestPermission(this) { granted -> toast("izin: $granted") } }
        button("login(\"kullanici-42\")") { Bildirim.login("kullanici-42") }
        button("logout()") { Bildirim.logout() }
        button("setTags(sehir=istanbul, plan=pro)") { Bildirim.setTags(mapOf("sehir" to "istanbul", "plan" to "pro")) }
        button("removeTags(plan)") { Bildirim.removeTags("plan") }
        button("track(satin_alma, 149.9 TRY)") { Bildirim.track("satin_alma", value = 149.9, currency = "TRY", properties = mapOf("urun" to "deneme")) }
        button("unsubscribe()") { Bildirim.unsubscribe() }
        button("subscribe()") { Bildirim.subscribe() }
        button("Yenile") { }

        setContentView(ScrollView(this).apply { addView(root) })
        handleDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun handleDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        toast("deep link: $data")
    }

    private fun refresh() {
        status.text = buildString {
            appendLine("SDK: ${io.bildirim.sdk.BildirimVersion.SDK_VERSION}")
            appendLine("Bildirimler açık: ${Bildirim.areNotificationsEnabled(this@MainActivity)}")
            appendLine("Jeton: ${Bildirim.getToken()?.take(24) ?: "(henüz yok)"}…")
            appendLine("Kurulum kimliği: ${Bildirim.getInstallationId()}")
            appendLine("externalId: ${Bildirim.getExternalId() ?: "-"}")
            appendLine("Etiketler: ${Bildirim.getTags()}")
            appendLine("Opt-out: ${Bildirim.isOptedOut()}")
            appendLine()
            appendLine("Ayrıntı için: adb logcat -s Bildirim:* Ornek:*")
        }
    }

    private fun toast(msg: String) = android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
}

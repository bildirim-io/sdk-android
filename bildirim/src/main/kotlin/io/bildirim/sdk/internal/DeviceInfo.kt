package io.bildirim.sdk.internal

import android.content.Context
import android.os.Build
import io.bildirim.sdk.BildirimVersion
import java.util.Locale
import java.util.TimeZone

/** Kayıtta gönderilen cihaz bilgisi. Reklam kimliği ve benzeri izleyici YOK (§3.6). */
internal class DeviceInfo(context: Context) {
    val appVersion: String = try {
        val pm = context.packageManager
        val info = pm.getPackageInfo(context.packageName, 0)
        (info.versionName ?: "").take(40)
    } catch (_: Exception) { "" }

    val sdkVersion: String = BildirimVersion.SDK_VERSION
    val timezone: String get() = TimeZone.getDefault().id.take(60)
    val country: String? get() = Locale.getDefault().country.takeIf { it.length == 2 }?.uppercase(Locale.ROOT)
    val userAgent: String = "BildirimAndroid/${BildirimVersion.SDK_VERSION} (Android ${Build.VERSION.SDK_INT}; ${context.packageName}/$appVersion)"

    /** Kayıt gövdesini etkileyen alanların parmak izi — değişmediyse ve 24 saat dolmadıysa yeniden kayıt yok. */
    fun fingerprint(token: String): String = listOf(token, appVersion, sdkVersion, timezone, country ?: "").joinToString("|")
}

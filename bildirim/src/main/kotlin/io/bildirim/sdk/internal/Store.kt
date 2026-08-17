package io.bildirim.sdk.internal

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.util.UUID

/**
 * Kalıcı durum. SharedPreferences bilinçli tercih: bağımlılık getirmez (DataStore coroutines +
 * datastore-core çeker, müşteri sürümüyle çakışabilir). FCM servisi ana süreçte çalıştığı için
 * çoklu süreç sorunu yok.
 */
internal class Store(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var appKey: String?
        get() = prefs.getString(K_APP_KEY, null)
        set(v) = put { putString(K_APP_KEY, v) }

    var apiBase: String?
        get() = prefs.getString(K_API_BASE, null)
        set(v) = put { putString(K_API_BASE, v) }

    var token: String?
        get() = prefs.getString(K_TOKEN, null)
        set(v) = put { putString(K_TOKEN, v) }

    /**
     * Kurulum kimliği: SDK'nın ürettiği rastgele UUID (reklam kimliği DEĞİL). Jeton yenilenince
     * sunucu bu kimliğe bakarak eski satırı yeni jetona taşır; aynı cihaz iki abone sayılmaz.
     */
    val installationId: String
        get() {
            prefs.getString(K_INSTALLATION_ID, null)?.let { return it }
            val id = UUID.randomUUID().toString()
            // Yarış olursa iki UUID üretilebilir; commit ile senkron yazıp tekrar okuyoruz.
            synchronized(this) {
                prefs.getString(K_INSTALLATION_ID, null)?.let { return it }
                prefs.edit().putString(K_INSTALLATION_ID, id).commit()
            }
            return id
        }

    var externalId: String?
        get() = prefs.getString(K_EXTERNAL_ID, null)
        set(v) = put { putString(K_EXTERNAL_ID, v) }

    /** Sunucuya en son gönderilen etiketlerin birleşimi (yalnız yerel görünüm için). */
    var tags: JSONObject
        get() = try { JSONObject(prefs.getString(K_TAGS, null) ?: "{}") } catch (_: Exception) { JSONObject() }
        set(v) = put { putString(K_TAGS, v.toString()) }

    var optedOut: Boolean
        get() = prefs.getBoolean(K_OPTED_OUT, false)
        set(v) = put { putBoolean(K_OPTED_OUT, v) }

    var lastSyncAt: Long
        get() = prefs.getLong(K_LAST_SYNC_AT, 0L)
        set(v) = put { putLong(K_LAST_SYNC_AT, v) }

    var lastSyncFingerprint: String?
        get() = prefs.getString(K_LAST_FINGERPRINT, null)
        set(v) = put { putString(K_LAST_FINGERPRINT, v) }

    var queueJson: String
        get() = prefs.getString(K_QUEUE, null) ?: "[]"
        set(v) { prefs.edit().putString(K_QUEUE, v).commit() } // kuyruk kritik: senkron yaz

    fun clearUserState() {
        put { remove(K_EXTERNAL_ID); remove(K_TAGS) }
    }

    private inline fun put(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
    }

    companion object {
        const val PREFS = "io.bildirim.sdk"
        private const val K_APP_KEY = "appKey"
        private const val K_API_BASE = "apiBase"
        private const val K_TOKEN = "token"
        private const val K_INSTALLATION_ID = "installationId"
        private const val K_EXTERNAL_ID = "externalId"
        private const val K_TAGS = "tags"
        private const val K_OPTED_OUT = "optedOut"
        private const val K_LAST_SYNC_AT = "lastSyncAt"
        private const val K_LAST_FINGERPRINT = "lastSyncFingerprint"
        private const val K_QUEUE = "queue"
    }
}

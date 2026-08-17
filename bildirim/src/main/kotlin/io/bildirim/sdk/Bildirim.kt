package io.bildirim.sdk

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import io.bildirim.sdk.internal.Api
import io.bildirim.sdk.internal.BildirimPermissionActivity
import io.bildirim.sdk.internal.Contract
import io.bildirim.sdk.internal.DeviceInfo
import io.bildirim.sdk.internal.FirebaseTokenProvider
import io.bildirim.sdk.internal.Lifecycle
import io.bildirim.sdk.internal.Log
import io.bildirim.sdk.internal.MessageHandler
import io.bildirim.sdk.internal.NotificationRenderer
import io.bildirim.sdk.internal.Payload
import io.bildirim.sdk.internal.Queue
import io.bildirim.sdk.internal.Store
import io.bildirim.sdk.internal.SyncEngine
import org.json.JSONObject

/**
 * Bildirim Android SDK — tek giriş noktası.
 *
 * ```kotlin
 * class App : Application() {
 *   override fun onCreate() {
 *     super.onCreate()
 *     Bildirim.initialize(this, "pk_...")
 *   }
 * }
 * // Bir Activity'de:
 * Bildirim.requestPermission(this) { granted -> }
 * Bildirim.login("kullanici-42"); Bildirim.setTags(mapOf("sehir" to "istanbul"))
 * Bildirim.track("satin_alma", value = 149.9, currency = "TRY")
 * Bildirim.setNotificationOpenedHandler { result -> false }
 * ```
 *
 * Tüm çağrılar iş parçacığından bağımsızdır; ağ işleri arka planda sıralı kuyrukla yürür ve
 * çevrimdışıyken kaybolmaz.
 */
public object Bildirim {

    internal class Runtime(val context: Context, val config: BildirimConfig) {
        val store = Store(context)
        val queue = Queue(store)
        val device = DeviceInfo(context)
        val api = Api(config.apiBase, config.appKey, device)
        val sync = SyncEngine(context, store, queue, api, device)
        val renderer = NotificationRenderer(context, config)
        val lifecycle = Lifecycle(onForeground = { sync.syncIfNeeded() }, onNetwork = { sync.onNetworkAvailable() })
        val messages = MessageHandler(
            renderer = renderer,
            sync = sync,
            isForeground = { lifecycle.isForeground },
            foregroundHandler = { foregroundHandler },
            showInForeground = config.showInForeground,
        )
    }

    @Volatile private var runtime: Runtime? = null
    @Volatile private var openedHandler: NotificationOpenedHandler? = null
    @Volatile private var foregroundHandler: ForegroundHandler? = null
    private val main = Handler(Looper.getMainLooper())

    // ---- kurulum ---------------------------------------------------------------------------

    /** `Application.onCreate` içinde çağırın. */
    @JvmStatic
    public fun initialize(context: Context, appKey: String) {
        initialize(context, BildirimConfig(appKey = appKey))
    }

    @JvmStatic
    public fun initialize(context: Context, config: BildirimConfig) {
        require(config.appKey.startsWith("pk_")) { "appKey 'pk_' ile başlamalı (Panel → Ayarlar → Anahtarlar)" }
        Log.level = config.logLevel
        val app = context.applicationContext
        val rt = Runtime(app, config)
        rt.store.appKey = config.appKey
        rt.store.apiBase = config.apiBase
        runtime = rt
        Log.i("Bildirim SDK ${BildirimVersion.SDK_VERSION} başlatıldı (${config.apiBase})")

        try { rt.renderer.ensureChannel() } catch (e: Exception) { Log.w("bildirim kanalı oluşturulamadı: ${e.message}") }
        rt.lifecycle.attach(app)

        val provider = config.tokenProvider ?: FirebaseTokenProvider
        provider.fetch { token -> if (token != null) setToken(app, token) }
        rt.sync.syncIfNeeded()
    }

    /**
     * Uygulamanın kendi FirebaseMessagingService'i varsa `onNewToken` içinden çağırın.
     * Aynı jeton tekrar verilirse hiçbir şey yapmaz.
     */
    @JvmStatic
    public fun setToken(context: Context, token: String) {
        val rt = ensure(context) ?: return
        if (rt.store.token == token) { rt.sync.syncIfNeeded(); return }
        Log.i("cihaz jetonu ${if (rt.store.token == null) "alındı" else "yenilendi"}")
        rt.store.token = token
        rt.sync.syncIfNeeded()
    }

    /**
     * Uygulamanın kendi FirebaseMessagingService'i varsa `onMessageReceived` içinden
     * `Bildirim.handleRemoteMessage(this, message.data)` çağırın. Mesaj Bildirim'e aitse işler
     * ve `true` döner; değilse dokunmaz, `false` döner (siz işlersiniz).
     */
    @JvmStatic
    public fun handleRemoteMessage(context: Context, data: Map<String, String>): Boolean {
        if (!Payload.isBildirim(data)) return false
        val rt = ensure(context) ?: return true
        val n = Payload.parse(data) ?: return true
        return try { rt.messages.handle(n) } catch (e: Exception) { Log.e("mesaj işlenemedi: ${e.message}", e); true }
    }

    /** `data` haritası Bildirim'den mi? */
    @JvmStatic
    public fun isBildirimMessage(data: Map<String, String>): Boolean = Payload.isBildirim(data)

    // ---- izin ---------------------------------------------------------------------------------

    /**
     * Bildirim iznini ister (Android 13+ sistem diyaloğu; altında zaten açık). Sonuç ana iş
     * parçacığında gelir. İzin verilince kayıt otomatik gider.
     */
    @JvmStatic
    public fun requestPermission(context: Context, callback: ((Boolean) -> Unit)? = null) {
        val rt = ensure(context) ?: run { callback?.invoke(false); return }
        val deliver: (Boolean) -> Unit = { granted ->
            if (granted) rt.sync.syncIfNeeded()
            callback?.let { cb -> main.post { cb(granted) } }
        }
        if (Build.VERSION.SDK_INT < 33) { deliver(rt.sync.notificationsEnabled()); return }
        if (context.checkSelfPermission(BildirimPermissionActivity.PERMISSION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            deliver(true); return
        }
        BildirimPermissionActivity.launch(context, deliver)
    }

    /** Bildirimler bu uygulama için açık mı (izin + sistem ayarı). */
    @JvmStatic
    public fun areNotificationsEnabled(context: Context): Boolean =
        ensure(context)?.sync?.notificationsEnabled() ?: false

    // ---- kimlik / etiket / olay ------------------------------------------------------------

    /** Kullanıcıyı dış kimlikle eşle (giriş). */
    @JvmStatic
    public fun login(externalId: String) {
        val rt = runtime ?: return notInit()
        val id = externalId.trim()
        if (id.isEmpty() || id.length > 255) { Log.e("login: externalId 1–255 karakter olmalı"); return }
        rt.store.externalId = id
        rt.sync.enqueueSubscribe(JSONObject().put("externalId", id))
    }

    /** Dış kimliği kaldır (çıkış). Cihaz abone kalır; yalnız kullanıcı bağı kopar. */
    @JvmStatic
    public fun logout() {
        val rt = runtime ?: return notInit()
        rt.store.clearUserState()
        rt.sync.enqueueSubscribe(JSONObject().put("externalId", JSONObject.NULL))
    }

    /**
     * Etiketleri birleştirerek yaz. Değeri `null` olan anahtar sunucuda silinir.
     * Değerler String/Number/Boolean olabilir.
     */
    @JvmStatic
    public fun setTags(tags: Map<String, Any?>) {
        val rt = runtime ?: return notInit()
        if (tags.isEmpty()) return
        val json = JSONObject()
        val local = rt.store.tags
        for ((k, v) in tags) {
            if (k.isBlank()) continue
            when (v) {
                null -> { json.put(k, JSONObject.NULL); local.remove(k) }
                is String, is Number, is Boolean -> { json.put(k, v); local.put(k, v) }
                else -> { json.put(k, v.toString()); local.put(k, v.toString()) }
            }
        }
        rt.store.tags = local
        rt.sync.enqueueSubscribe(JSONObject().put("tags", json))
    }

    /** Etiket sil (kısayol: değerleri null olan setTags). */
    @JvmStatic
    public fun removeTags(vararg keys: String) {
        setTags(keys.associateWith { null })
    }

    /** Yerelde bilinen etiketler (sunucuya en son gönderilenlerin birleşimi). */
    @JvmStatic
    public fun getTags(): Map<String, Any> {
        val rt = runtime ?: return emptyMap()
        val obj = rt.store.tags
        val out = LinkedHashMap<String, Any>()
        obj.keys().forEach { k -> obj.opt(k)?.let { out[k] = it } }
        return out
    }

    /**
     * Özel olay (dönüşüm). `name`: `^[a-zA-Z0-9_.:-]+$`, en çok 60 karakter.
     * Kampanya ilişkilendirmesini sunucu yapar (`attributed`).
     */
    @JvmStatic
    @JvmOverloads
    public fun track(name: String, value: Double? = null, currency: String? = null, properties: Map<String, Any?>? = null) {
        val rt = runtime ?: return notInit()
        if (!EVENT_NAME.matches(name)) { Log.e("track: geçersiz olay adı '$name' (izinli: harf, rakam, _ . : - ; ≤60)"); return }
        val props = properties?.takeIf { it.isNotEmpty() }?.let { m ->
            JSONObject().also { j -> m.forEach { (k, v) -> j.put(k, v ?: JSONObject.NULL) } }
        }
        rt.sync.enqueueTrack(name, value, currency?.take(3)?.uppercase(), props)
    }

    // ---- abonelik ------------------------------------------------------------------------------

    /** `unsubscribe()` sonrası yeniden abone ol. İlk kayıt için gerekmez; izin yeterli. */
    @JvmStatic
    public fun subscribe() {
        val rt = runtime ?: return notInit()
        rt.store.optedOut = false
        rt.store.lastSyncAt = 0L
        rt.sync.enqueueSubscribe(null)
    }

    /** Aboneliği sonlandır: sunucudaki kayıt pasifleşir, otomatik yeniden kayıt durur. */
    @JvmStatic
    public fun unsubscribe() {
        val rt = runtime ?: return notInit()
        rt.store.optedOut = true
        rt.store.lastSyncAt = 0L
        rt.sync.enqueueUnsubscribe()
    }

    /** Kullanıcı `unsubscribe()` çağırdı mı. */
    @JvmStatic
    public fun isOptedOut(): Boolean = runtime?.store?.optedOut ?: false

    /** Bilinen cihaz jetonu (henüz alınmadıysa null). */
    @JvmStatic
    public fun getToken(): String? = runtime?.store?.token

    /** SDK'nın ürettiği kurulum kimliği. */
    @JvmStatic
    public fun getInstallationId(): String? = runtime?.store?.installationId

    /** Yerelde bilinen dış kimlik. */
    @JvmStatic
    public fun getExternalId(): String? = runtime?.store?.externalId

    // ---- handler'lar ------------------------------------------------------------------------

    /** Bildirime dokunulduğunda (gövde ya da aksiyon düğmesi). Ana iş parçacığında çağrılır. */
    @JvmStatic
    public fun setNotificationOpenedHandler(handler: NotificationOpenedHandler?) { openedHandler = handler }

    /** Uygulama ön plandayken bildirim gelince. `true` → SDK çizer. */
    @JvmStatic
    public fun setForegroundHandler(handler: ForegroundHandler?) { foregroundHandler = handler }

    // ---- iç köprüler (Activity/Receiver'dan) ------------------------------------------------

    internal fun internalReportEvent(context: Context, n: BildirimNotification, event: String, actionId: String?) {
        val rt = ensure(context) ?: return
        val t = n.eventToken ?: return
        rt.sync.enqueueEvent(t, event, actionId)
    }

    internal fun internalCancelNotification(context: Context, id: Int) {
        try { ensure(context)?.renderer?.cancel(id) } catch (_: Exception) {}
    }

    /** Döner: uygulama yönlendirmeyi üstlendi mi. */
    internal fun internalDispatchOpened(result: BildirimOpenResult): Boolean {
        val h = openedHandler ?: return false
        return try { h.onOpened(result) } catch (e: Exception) { Log.w("opened handler hata: ${e.message}"); false }
    }

    // ---- yardımcılar ------------------------------------------------------------------------

    /**
     * Çalışma zamanını getirir; `initialize` çağrılmadıysa (süreç FCM tarafından uyandırıldı ama
     * uygulama Application.onCreate'te başlatmıyor) kalıcı ayarlardan yeniden kurar.
     */
    private fun ensure(context: Context): Runtime? {
        runtime?.let { return it }
        synchronized(this) {
            runtime?.let { return it }
            val store = Store(context.applicationContext)
            val key = store.appKey
            if (key == null) {
                Log.e("Bildirim.initialize çağrılmamış. Application.onCreate içinde Bildirim.initialize(this, \"pk_...\") çağırın.")
                return null
            }
            Log.w("initialize çağrılmadan kullanıldı; kalıcı ayarlarla devam ediliyor")
            val rt = Runtime(context.applicationContext, BildirimConfig(appKey = key, apiBase = store.apiBase ?: BildirimConfig.DEFAULT_API_BASE))
            runtime = rt
            rt.lifecycle.attach(context.applicationContext)
            return rt
        }
    }

    private fun notInit() { Log.e("Bildirim.initialize çağrılmamış") }

    private val EVENT_NAME = Regex("^[a-zA-Z0-9_.:-]{1,60}$")

    /** Test için: durumu sıfırla. */
    internal fun resetForTests() { runtime = null; openedHandler = null; foregroundHandler = null }

    internal fun runtimeForTests(): Runtime? = runtime
}

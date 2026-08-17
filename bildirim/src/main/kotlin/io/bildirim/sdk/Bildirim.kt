package io.bildirim.sdk

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.google.firebase.messaging.RemoteMessage
import io.bildirim.sdk.internal.Api
import io.bildirim.sdk.internal.BildirimPermissionActivity
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
 * class UygulamaniZ : Application() {
 *   override fun onCreate() {
 *     super.onCreate()
 *     Bildirim.initialize(this, "pk_sizin_anahtariniz")
 *   }
 * }
 * // Kullanıcı bir değer gördükten sonra:
 * Bildirim.requestPermission { granted -> }
 * Bildirim.login("kullanici-42")
 * Bildirim.setTags(mapOf("sehir" to "istanbul"))
 * Bildirim.track("satin_alma", mapOf("value" to 149.9, "currency" to "TRY"))
 * ```
 *
 * Tüm çağrılar iş parçacığından bağımsızdır; ağ işleri arka planda sıralı kuyrukla yürür ve
 * çevrimdışıyken kaybolmaz.
 */
public object Bildirim {

    internal class Runtime(val context: Context, val config: BildirimConfig, val appKey: String) {
        val store = Store(context)
        val queue = Queue(store)
        val device = DeviceInfo(context)
        val api = Api(config.apiBase, appKey, device)
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

    /** `Application.onCreate` içinde çağırın. `pk_` anahtarı: Panel → Ayarlar → API anahtarları. */
    @JvmStatic
    public fun initialize(context: Context, appKey: String) {
        initialize(context, appKey, BildirimConfig())
    }

    /** Yapılandırmalı kurulum (kendi sunucunuz, kanal adı, simge, renk). */
    @JvmStatic
    public fun initialize(context: Context, appKey: String, config: BildirimConfig) {
        require(appKey.startsWith("pk_")) { "appKey 'pk_' ile başlamalı (Panel → Ayarlar → API anahtarları)" }
        Log.level = config.logLevel
        val app = context.applicationContext
        val rt = Runtime(app, config, appKey)
        rt.store.appKey = appKey
        rt.store.apiBase = config.apiBase
        runtime = rt
        Log.i("Bildirim SDK ${BildirimVersion.SDK_VERSION} başlatıldı (${config.apiBase})")

        try { rt.renderer.ensureChannel() } catch (e: Exception) { Log.w("bildirim kanalı oluşturulamadı: ${e.message}") }
        rt.lifecycle.attach(app)

        val provider = config.tokenProvider ?: FirebaseTokenProvider
        provider.fetch { token -> if (token != null) internalSetToken(app, token) }
        rt.sync.syncIfNeeded()
    }

    // ---- kendi FirebaseMessagingService'i olan uygulamalar için köprüler --------------------

    /**
     * Kendi `FirebaseMessagingService`'inizin `onNewToken`'ından çağırın.
     * Aynı jeton tekrar verilirse ağa çıkılmaz.
     */
    @JvmStatic
    public fun onNewToken(token: String) {
        val ctx = runtime?.context ?: return notInit()
        internalSetToken(ctx, token)
    }

    /**
     * Kendi `FirebaseMessagingService`'inizin `onMessageReceived`'ından çağırın. Mesaj
     * Bildirim'e aitse (`data.bildirim`) işler ve `true` döner; değilse dokunmaz, `false` döner.
     */
    @JvmStatic
    public fun onMessageReceived(message: RemoteMessage): Boolean = onMessageReceived(message.data)

    /** Ham `data` haritasıyla aynı iş (HMS gibi başka bir taşıyıcı kullanıyorsanız). */
    @JvmStatic
    public fun onMessageReceived(data: Map<String, String>): Boolean {
        if (!Payload.isBildirim(data)) return false
        val ctx = runtime?.context ?: run { notInit(); return true }
        return internalHandleData(ctx, data)
    }

    /** Mesaj Bildirim'den mi? */
    @JvmStatic
    public fun isBildirimMessage(message: RemoteMessage): Boolean = Payload.isBildirim(message.data)

    @JvmStatic
    public fun isBildirimMessage(data: Map<String, String>): Boolean = Payload.isBildirim(data)

    // ---- izin ---------------------------------------------------------------------------------

    /**
     * Bildirim iznini ister (Android 13+ sistem penceresi; altında izin yoktur). İzin verilirse
     * cihaz kaydı otomatik gider. Sonuç ana iş parçacığında.
     *
     * Uygulama açılır açılmaz değil, kullanıcı bir değer gördükten sonra çağırın.
     */
    @JvmStatic
    @JvmOverloads
    public fun requestPermission(callback: ((Boolean) -> Unit)? = null) {
        val ctx = runtime?.context ?: run { notInit(); callback?.invoke(false); return }
        requestPermission(ctx, callback)
    }

    /** Belirli bir context (Activity) ile izin isteme. */
    @JvmStatic
    public fun requestPermission(context: Context, callback: ((Boolean) -> Unit)?) {
        val rt = ensure(context) ?: run { callback?.invoke(false); return }
        val deliver: (Boolean) -> Unit = { granted ->
            if (granted) rt.sync.syncIfNeeded()
            callback?.let { cb -> main.post { cb(granted) } }
        }
        if (Build.VERSION.SDK_INT < 33) { deliver(rt.sync.notificationsEnabled()); return }
        if (context.checkSelfPermission(BildirimPermissionActivity.PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            deliver(true); return
        }
        BildirimPermissionActivity.launch(context, deliver)
    }

    /** Bildirimler bu uygulama için açık mı (izin + sistem ayarı). */
    @JvmStatic
    @JvmOverloads
    public fun areNotificationsEnabled(context: Context? = null): Boolean {
        val rt = context?.let { ensure(it) } ?: runtime ?: return false
        return rt.sync.notificationsEnabled()
    }

    // ---- kimlik / etiket / olay ------------------------------------------------------------

    /** Kullanıcıyı dış kimlikle eşle (giriş). Sunucudan `externalIds` ile hedeflenir. */
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
     * Etiketleri birleştirerek yaz — her çağrıda hepsini göndermeniz gerekmez.
     * Değeri `null` olan anahtar sunucuda silinir. Değerler String/Number/Boolean olabilir.
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

    /** Etiket sil (kısayol: değerleri null olan [setTags]). */
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
     * Özel olay (dönüşüm). Son 24 saatte tıklanan kampanyaya sunucu tarafından bağlanır.
     * `properties` içindeki `value` (sayı) ve `currency` (3 harf) ciro olarak raporlanır.
     *
     * `name`: `^[a-zA-Z0-9_.:-]+$`, en çok 60 karakter.
     */
    @JvmStatic
    @JvmOverloads
    public fun track(name: String, properties: Map<String, Any?>? = null) {
        val rt = runtime ?: return notInit()
        if (!EVENT_NAME.matches(name)) { Log.e("track: geçersiz olay adı '$name' (izinli: harf, rakam, _ . : - ; ≤60)"); return }
        var value: Double? = null
        var currency: String? = null
        var props: JSONObject? = null
        properties?.forEach { (k, v) ->
            when {
                k == "value" && v is Number -> value = v.toDouble()
                k == "currency" && v is String -> currency = v.take(3).uppercase()
                else -> {
                    val j = props ?: JSONObject().also { props = it }
                    j.put(k, v ?: JSONObject.NULL)
                }
            }
        }
        rt.sync.enqueueTrack(name, value, currency, props)
    }

    // ---- abonelik ------------------------------------------------------------------------------

    /** [unsubscribe] sonrası yeniden abone ol. İlk kayıt için gerekmez; izin yeterli. */
    @JvmStatic
    public fun subscribe() {
        val rt = runtime ?: return notInit()
        rt.store.optedOut = false
        rt.store.lastSyncAt = 0L
        rt.sync.enqueueSubscribe(null)
    }

    /** Kullanıcı bildirimleri kapattı: sunucudaki kayıt pasifleşir, otomatik yeniden kayıt durur. */
    @JvmStatic
    public fun unsubscribe() {
        val rt = runtime ?: return notInit()
        rt.store.optedOut = true
        rt.store.lastSyncAt = 0L
        rt.sync.enqueueUnsubscribe()
    }

    /** Kullanıcı [unsubscribe] çağırdı mı. */
    @JvmStatic
    public fun isOptedOut(): Boolean = runtime?.store?.optedOut ?: false

    /** Bilinen FCM cihaz jetonu (henüz alınmadıysa null). */
    @JvmStatic
    public fun getToken(): String? = runtime?.store?.token

    /** SDK'nın ürettiği kalıcı kurulum kimliği (reklam kimliği DEĞİL). */
    @JvmStatic
    public fun getInstallationId(): String? = runtime?.store?.installationId

    /** Yerelde bilinen dış kimlik. */
    @JvmStatic
    public fun getExternalId(): String? = runtime?.store?.externalId

    // ---- handler'lar ------------------------------------------------------------------------

    /**
     * Bildirime (ya da aksiyon düğmesine) dokunulduğunda ana iş parçacığında çağrılır.
     * `true` dönerse SDK varsayılan açmayı yapmaz.
     */
    @JvmStatic
    public fun setNotificationOpenedHandler(handler: NotificationOpenedHandler?) { openedHandler = handler }

    /** Uygulama ön plandayken bildirim gelince. `true` → SDK çizsin. */
    @JvmStatic
    public fun setForegroundHandler(handler: ForegroundHandler?) { foregroundHandler = handler }

    // ---- iç köprüler ------------------------------------------------------------------------

    internal fun internalSetToken(context: Context, token: String) {
        val rt = ensure(context) ?: return
        if (rt.store.token == token) { rt.sync.syncIfNeeded(); return }
        Log.i("cihaz jetonu ${if (rt.store.token == null) "alındı" else "yenilendi"}")
        rt.store.token = token
        rt.sync.syncIfNeeded()
    }

    internal fun internalHandleData(context: Context, data: Map<String, String>): Boolean {
        if (!Payload.isBildirim(data)) return false
        val rt = ensure(context) ?: return true
        val n = Payload.parse(data) ?: return true
        return try { rt.messages.handle(n) } catch (e: Exception) { Log.e("mesaj işlenemedi: ${e.message}", e); true }
    }

    internal fun internalReportEvent(context: Context, n: BildirimNotification, event: String, actionId: String?) {
        val rt = ensure(context) ?: return
        val t = n.eventToken ?: return
        rt.sync.enqueueEvent(t, event, actionId)
    }

    internal fun internalCancelNotification(context: Context, id: Int) {
        try { ensure(context)?.renderer?.cancel(id) } catch (_: Exception) {}
    }

    /** Döner: uygulama yönlendirmeyi üstlendi mi. */
    internal fun internalDispatchOpened(notification: BildirimNotification): Boolean {
        val h = openedHandler ?: return false
        return try { h.onOpened(notification) } catch (e: Exception) { Log.w("opened handler hata: ${e.message}"); false }
    }

    // ---- yardımcılar ------------------------------------------------------------------------

    /**
     * Çalışma zamanını getirir; `initialize` çağrılmadıysa (süreç FCM tarafından uyandırıldı ama
     * uygulama `Application.onCreate`'te başlatmıyor) kalıcı ayarlardan yeniden kurar.
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
            val rt = Runtime(
                context.applicationContext,
                BildirimConfig(apiBase = store.apiBase ?: BildirimConfig.DEFAULT_API_BASE),
                key,
            )
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

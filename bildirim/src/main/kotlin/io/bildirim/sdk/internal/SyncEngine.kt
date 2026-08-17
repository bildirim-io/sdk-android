package io.bildirim.sdk.internal

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tüm ağ akışı buradan geçer: kuyruğa alma, "ne zaman kayıt" kuralı (§3.3), sıralı boşaltma,
 * üstel bekleme. Tek iş parçacıklı executor → sıra garantisi.
 *
 * Kayıt (subscribe) yalnız şu durumlarda gider: ilk kayıt, jeton değişti, login/logout/setTags,
 * son başarılı kayıttan 24 saat geçti ya da cihaz parmak izi (sürüm/dil/saat dilimi) değişti.
 */
internal class SyncEngine(
    private val context: Context,
    private val store: Store,
    private val queue: Queue,
    private val api: Api,
    private val device: DeviceInfo,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val executor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "bildirim-sync").apply { isDaemon = true }
    }
    private val draining = AtomicBoolean(false)
    private var retryFuture: ScheduledFuture<*>? = null
    private var failureCount = 0
    /** Retry-After / backoff: bu andan önce ağa çıkılmaz (yalnız ağ-geldi tetikleyicisi sıfırlar). */
    @Volatile private var notBefore = 0L
    private var lastAutoRegisterFp: String? = null
    private var lastAutoRegisterAt = 0L

    /** Test kancası: bildirim izni denetimi. */
    internal var notificationsEnabled: () -> Boolean = { defaultNotificationsEnabled() }

    // ---- kuyruğa alma -------------------------------------------------------------------

    fun enqueueSubscribe(delta: JSONObject?) {
        queue.add(Queue.KIND_SUBSCRIBE, delta ?: JSONObject())
        drain()
    }

    fun enqueueUnsubscribe() {
        queue.add(Queue.KIND_UNSUBSCRIBE, JSONObject())
        drain()
    }

    fun enqueueTrack(name: String, value: Double?, currency: String?, properties: JSONObject?) {
        val d = JSONObject().put("name", name)
        value?.let { d.put("value", it) }
        currency?.let { d.put("currency", it) }
        properties?.let { d.put("properties", it) }
        queue.add(Queue.KIND_TRACK, d)
        drain()
    }

    fun enqueueEvent(eventToken: String, event: String, actionId: String?) {
        val d = JSONObject().put("t", eventToken).put("event", event)
        actionId?.let { d.put("actionId", it) }
        queue.add(Queue.KIND_EVENT, d)
        drain()
    }

    // ---- kayıt kuralı --------------------------------------------------------------------

    /**
     * Gerekiyorsa kayıt öğesi ekler ve boşaltmayı tetikler. Her uygulama açılışında çağrılır;
     * çoğu zaman hiçbir şey göndermez (CGNAT + oran sınırı disiplini).
     */
    fun syncIfNeeded() {
        executor.execute {
            val reason = registerReason()
            if (reason != null) {
                val fp = device.fingerprint(store.token ?: "")
                val recentlyTried = lastAutoRegisterFp == fp && clock() - lastAutoRegisterAt < AUTO_REGISTER_DEBOUNCE_MS
                if (!queue.any(Queue.KIND_SUBSCRIBE) && !recentlyTried) {
                    Log.d("kayıt gerekiyor: $reason")
                    queue.add(Queue.KIND_SUBSCRIBE, JSONObject())
                    lastAutoRegisterFp = fp; lastAutoRegisterAt = clock()
                }
            }
            drainNow()
        }
    }

    /** null → kayıt gerekmiyor; aksi halde sebep (log için). Test edilebilir saf kural. */
    internal fun registerReason(): String? {
        if (store.optedOut) return null
        val token = store.token ?: return null
        if (!notificationsEnabled()) return null
        val fp = device.fingerprint(token)
        val last = store.lastSyncAt
        return when {
            last == 0L -> "ilk kayıt"
            store.lastSyncFingerprint != fp -> "cihaz/jeton değişti"
            clock() - last >= Contract.REREGISTER_INTERVAL_MS -> "24 saat doldu"
            else -> null
        }
    }

    // ---- boşaltma ------------------------------------------------------------------------

    fun drain() { executor.execute { drainNow() } }

    /** Ağ geri geldi: bekleme penceresini sıfırla ve hemen dene. */
    fun onNetworkAvailable() { executor.execute { notBefore = 0L; drainNow() } }

    private fun drainNow() {
        if (!draining.compareAndSet(false, true)) return
        try {
            if (clock() < notBefore) { Log.d("bekleme penceresi (${(notBefore - clock()) / 1000} sn), kuyruk bekliyor"); return }
            retryFuture?.cancel(false); retryFuture = null
            var registerPrepended = false
            while (true) {
                val item = queue.peek() ?: break
                val token = store.token
                if (token == null) { Log.d("jeton yok, kuyruk bekliyor (${queue.size()} öğe)"); break }
                if (!registerPrepended && item.kind != Queue.KIND_SUBSCRIBE && item.kind != Queue.KIND_UNSUBSCRIBE &&
                    store.lastSyncAt == 0L && !store.optedOut
                ) {
                    // Sunucu bu cihazı hiç görmedi; track/event'ten önce kayıt gitmeli.
                    // Bir kez denenir: kayıt reddedilirse (403 vb.) döngüye girmeden sıradakiyle devam edilir.
                    registerPrepended = true
                    queue.addFirst(Queue.KIND_SUBSCRIBE, JSONObject())
                    continue
                }
                val result = send(item, token)
                when (result) {
                    is Http.Result.Ok -> {
                        onSuccess(item, token, result)
                        queue.remove(item.id)
                        failureCount = 0
                    }
                    is Http.Result.Drop -> {
                        onDrop(item, result)
                        queue.remove(item.id)
                    }
                    is Http.Result.Retry -> {
                        Log.w("${item.kind}: sunucu ${result.status}, bekleniyor")
                        scheduleRetry(result.retryAfterMs)
                        break
                    }
                    is Http.Result.NetworkError -> {
                        Log.d("${item.kind}: ağ hatası (${result.error.javaClass.simpleName}), bekleniyor")
                        scheduleRetry(null)
                        break
                    }
                }
            }
        } finally {
            draining.set(false)
        }
    }

    private fun send(item: Queue.Item, token: String): Http.Result = when (item.kind) {
        Queue.KIND_SUBSCRIBE -> api.subscribe(api.subscribeBody(token, store.installationId, item.delta))
        Queue.KIND_UNSUBSCRIBE -> api.unsubscribe(api.unsubscribeBody(token))
        Queue.KIND_TRACK -> api.track(api.trackBody(token, item.delta))
        Queue.KIND_EVENT -> api.notificationEvent(api.eventBody(token, item.delta))
        else -> Http.Result.Drop(0, null)
    }

    private fun onSuccess(item: Queue.Item, token: String, r: Http.Result.Ok) {
        when (item.kind) {
            Queue.KIND_SUBSCRIBE -> {
                store.lastSyncAt = clock()
                store.lastSyncFingerprint = device.fingerprint(token)
                Log.i("kayıt tamam (${r.body?.optString("status") ?: r.status})")
            }
            Queue.KIND_UNSUBSCRIBE -> Log.i("abonelikten çıkıldı")
            Queue.KIND_EVENT -> {
                val status = r.body?.optString("status")
                if (status == "ignored") Log.d("olay yok sayıldı (jeton süresi geçmiş) — hata değil")
            }
        }
    }

    private fun onDrop(item: Queue.Item, r: Http.Result.Drop) {
        val err = r.body?.optString("error") ?: ""
        val msg = r.body?.optString("message") ?: ""
        when (r.status) {
            401 -> Log.e("${item.kind}: proje anahtarı geçersiz ya da iptal edilmiş (401). appKey'i kontrol edin.")
            402 -> Log.e("${item.kind}: plan abone sınırı doldu (402). $msg")
            403 -> Log.e("${item.kind}: $msg (403) — Panel → Ayarlar → Mobil Push")
            400 -> Log.e("${item.kind}: geçersiz istek (400) $err ${r.body?.optJSONArray("issues") ?: msg}")
            else -> Log.e("${item.kind}: reddedildi (${r.status}) $err $msg")
        }
    }

    private fun scheduleRetry(retryAfterMs: Long?) {
        failureCount++
        val backoff = BACKOFF_MS[minOf(failureCount - 1, BACKOFF_MS.size - 1)]
        val delay = (retryAfterMs ?: backoff).coerceIn(1_000L, MAX_DELAY_MS)
        notBefore = clock() + delay
        retryFuture?.cancel(false)
        retryFuture = executor.schedule({ drainNow() }, delay, TimeUnit.MILLISECONDS)
        Log.d("yeniden deneme ${delay / 1000} sn sonra")
    }

    private fun defaultNotificationsEnabled(): Boolean {
        if (Build.VERSION.SDK_INT < 24) return true
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return true
        return nm.areNotificationsEnabled()
    }

    /** Test için: kuyruk boşalana ya da duruncaya kadar bekle. */
    internal fun awaitIdle(timeoutMs: Long = 5_000) {
        executor.submit {}.get(timeoutMs, TimeUnit.MILLISECONDS)
    }

    companion object {
        private val BACKOFF_MS = longArrayOf(60_000L, 5 * 60_000L, 15 * 60_000L, 60 * 60_000L)
        private const val MAX_DELAY_MS = 60 * 60_000L
        private const val AUTO_REGISTER_DEBOUNCE_MS = 60_000L
    }
}

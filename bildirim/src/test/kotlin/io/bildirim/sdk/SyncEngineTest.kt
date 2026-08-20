package io.bildirim.sdk

import android.content.Context
import io.bildirim.sdk.internal.Api
import io.bildirim.sdk.internal.Contract
import io.bildirim.sdk.internal.DeviceInfo
import io.bildirim.sdk.internal.Queue
import io.bildirim.sdk.internal.Store
import io.bildirim.sdk.internal.SyncEngine
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SyncEngineTest {
    private val ctx: Context = RuntimeEnvironment.getApplication()
    private lateinit var api: FakeApi

    @Before fun setUp() { api = FakeApi(); Bildirim.resetForTests() }
    @After fun tearDown() { api.close(); Bildirim.resetForTests() }

    private fun init(token: String? = "tok-1", key: String = "pk_test") {
        Bildirim.initialize(ctx, key, BildirimConfig(apiBase = api.baseUrl, tokenProvider = FakeTokenProvider(token), logLevel = BildirimConfig.LOG_DEBUG))
    }

    private fun idle() = Bildirim.runtimeForTests()!!.sync.awaitIdle()

    @Test fun `ilk kayit tum alanlarla gider, ikinci acilis gondermez`() {
        init(); idle()
        assertEquals(listOf("/v1/subscribe"), api.paths())
        val b = api.requests[0].body!!
        assertEquals("pk_test", b.getString("projectKey"))
        assertEquals("android", b.getString("channel"))
        assertEquals("tok-1", b.getString("token"))
        assertEquals("android", b.getString("os"))
        assertEquals(BildirimVersion.SDK_VERSION, b.getString("sdkVersion"))
        assertTrue(b.getString("installationId").length >= 32)
        assertTrue(b.has("timezone")); assertTrue(b.has("appVersion"))
        assertTrue(!b.has("externalId")); assertTrue(!b.has("tags"))
        val inst = b.getString("installationId")

        // aynı süreçte yeniden başlat: jeton aynı, 24 saat dolmadı → istek yok
        Bildirim.resetForTests(); init(); idle()
        assertEquals(1, api.requests.size)
        assertEquals(inst, Bildirim.getInstallationId())
    }

    @Test fun `jeton yenilenince yeniden kayit`() {
        init(); idle()
        Bildirim.onNewToken("tok-2"); idle()
        assertEquals(2, api.requests.size)
        assertEquals("tok-2", api.requests[1].body!!.getString("token"))
        Bildirim.onNewToken("tok-2"); idle()
        assertEquals(2, api.requests.size)
    }

    @Test fun `login setTags logout sirasi cevrimdisinda korunur`() {
        api.down = true
        init(); idle()
        Bildirim.login("u1")
        Bildirim.setTags(mapOf("sehir" to "istanbul", "yas" to 30))
        Bildirim.logout()
        Bildirim.setTags(mapOf("sehir" to null))
        Bildirim.track("satin_alma", mapOf("value" to 149.9, "currency" to "try", "urun" to "x"))
        idle()
        api.requests.clear()
        api.down = false
        Bildirim.runtimeForTests()!!.sync.onNetworkAvailable(); idle()

        val paths = api.paths()
        assertEquals(listOf("/v1/subscribe", "/v1/subscribe", "/v1/subscribe", "/v1/subscribe", "/v1/subscribe", "/v1/events"), paths)
        val bodies = api.requests.map { it.body!! }
        assertTrue(!bodies[0].has("externalId"))                       // ilk kayıt
        assertEquals("u1", bodies[1].getString("externalId"))          // login
        assertEquals("istanbul", bodies[2].getJSONObject("tags").getString("sehir"))
        assertEquals(30, bodies[2].getJSONObject("tags").getInt("yas"))
        assertTrue(bodies[3].has("externalId") && bodies[3].isNull("externalId")) // logout → null
        assertTrue(bodies[4].getJSONObject("tags").isNull("sehir"))    // etiket silme → null
        assertEquals("satin_alma", bodies[5].getString("name"))
        assertEquals(149.9, bodies[5].getDouble("value"), 0.001)
        assertEquals("TRY", bodies[5].getString("currency"))
        assertEquals("x", bodies[5].getJSONObject("properties").getString("urun"))
        assertEquals("tok-1", bodies[5].getString("token"))
        assertEquals(0, Bildirim.runtimeForTests()!!.queue.size())
    }

    @Test fun `403 kalici hata, dongusuz duser, sonraki ogeler devam eder`() {
        api.enqueue("/v1/subscribe", 403, """{"error":"forbidden","message":"FCM yapılandırılmamış"}""")
        api.enqueue("/v1/subscribe", 403, """{"error":"forbidden","message":"FCM yapılandırılmamış"}""")
        init(); idle()
        Bildirim.track("olay"); idle()
        assertEquals(listOf("/v1/subscribe", "/v1/subscribe", "/v1/events"), api.paths())
        assertEquals(0, Bildirim.runtimeForTests()!!.queue.size())
    }

    @Test fun `429 retry-after ile bekler ve tekrar dener`() {
        api.enqueue("/v1/subscribe", 429, """{"error":"rate_limited"}""", mapOf("Retry-After" to "1"))
        init(); idle()
        assertEquals(1, api.requests.size)
        assertEquals(1, Bildirim.runtimeForTests()!!.queue.size())
        // bekleme penceresi içinde açık drain de ağa çıkmaz
        Bildirim.runtimeForTests()!!.sync.drain(); idle()
        assertEquals(1, api.requests.size)
        Thread.sleep(1_600); idle()
        assertEquals(2, api.requests.size)
        assertEquals(0, Bildirim.runtimeForTests()!!.queue.size())
    }

    @Test fun `ignored olay hata degil`() {
        api.enqueue("/v1/mobile/events", 200, """{"status":"ignored"}""")
        init(); idle()
        Bildirim.runtimeForTests()!!.sync.enqueueEvent("t-eski", Contract.EV_DISPLAYED, null); idle()
        assertEquals("/v1/mobile/events", api.paths().last())
        val b = api.requests.last().body!!
        assertEquals("t-eski", b.getString("t")); assertEquals("displayed", b.getString("event")); assertEquals("tok-1", b.getString("token"))
        assertEquals(0, Bildirim.runtimeForTests()!!.queue.size())
    }

    @Test fun `unsubscribe sonrasi otomatik kayit yok, subscribe geri acar`() {
        init(); idle()
        Bildirim.unsubscribe(); idle()
        assertEquals("/v1/unsubscribe", api.paths().last())
        assertTrue(Bildirim.isOptedOut())
        Bildirim.runtimeForTests()!!.sync.syncIfNeeded(); idle()
        assertEquals(2, api.requests.size)
        Bildirim.subscribe(); idle()
        assertEquals("/v1/subscribe", api.paths().last())
        assertTrue(!Bildirim.isOptedOut())
    }

    @Test fun `jeton yokken kuyruk bekler`() {
        init(token = null); idle()
        Bildirim.login("u1"); idle()
        assertEquals(0, api.requests.size)
        assertEquals(1, Bildirim.runtimeForTests()!!.queue.size())
        Bildirim.onNewToken("tok-9"); idle()
        // login öğesi tam kayıt gövdesi taşır; ayrıca boş kayıt gönderilmez
        assertEquals(listOf("/v1/subscribe"), api.paths())
        assertEquals("u1", api.requests[0].body!!.getString("externalId"))
        assertEquals("tok-9", api.requests[0].body!!.getString("token"))
        assertTrue(Bildirim.runtimeForTests()!!.store.lastSyncAt > 0)
    }

    /** Kimlik doğrulama açık projede imzasız login: 403 döner, öğe düşer, kuyruk tıkanmaz. */
    @Test fun `identity_verification_required kalici hata olarak duser`() {
        init(); idle()
        api.enqueue("/v1/subscribe", 403, """{"error":"identity_verification_required","message":"İmza gerekli"}""")
        Bildirim.login("u1"); idle()
        assertEquals("/v1/subscribe", api.paths().last())
        assertEquals(0, Bildirim.runtimeForTests()!!.queue.size())
        // Sonraki çağrılar akmaya devam eder
        Bildirim.track("olay"); idle()
        assertEquals("/v1/events", api.paths().last())
    }

    @Test fun `gecersiz olay adi reddedilir`() {
        init(); idle()
        Bildirim.track("bosluk var"); Bildirim.track("a".repeat(61)); idle()
        assertEquals(1, api.requests.size)
    }

    @Test fun `kayit kurali - 24 saat ve parmak izi`() {
        var now = 1_000_000L
        val store = Store(ctx)
        val device = DeviceInfo(ctx)
        val engine = SyncEngine(ctx, store, Queue(store), Api(api.baseUrl, "pk_x", device), device, clock = { now })
        assertNull(engine.registerReason())                     // jeton yok
        store.token = "t1"
        assertEquals("ilk kayıt", engine.registerReason())
        store.lastSyncAt = now; store.lastSyncFingerprint = device.fingerprint("t1")
        assertNull(engine.registerReason())
        now += Contract.REREGISTER_INTERVAL_MS - 1
        assertNull(engine.registerReason())
        now += 1
        assertEquals("24 saat doldu", engine.registerReason())
        store.lastSyncAt = now
        store.token = "t2"
        assertEquals("cihaz/jeton değişti", engine.registerReason())
        store.optedOut = true
        assertNull(engine.registerReason())
        store.optedOut = false
        engine.notificationsEnabled = { false }
        assertNull(engine.registerReason())
    }

    @Test fun `initialize olmadan servis cagrisi kalici ayarla calisir`() {
        init(); idle()
        Bildirim.resetForTests()
        // Süreç FCM ile uyanmış, initialize koşmamış gibi: servis köprüsü kalıcı appKey ile çalışmalı
        Bildirim.internalSetToken(ctx, "tok-yeni")
        Bildirim.runtimeForTests()!!.sync.awaitIdle()
        assertNotNull(Bildirim.runtimeForTests())
        assertEquals("tok-yeni", api.requests.last().body!!.getString("token"))
    }
}

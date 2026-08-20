package io.bildirim.sdk

import android.content.Context
import android.content.Intent
import io.bildirim.sdk.internal.Payload
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * `docs/android.md` (yayında: bildirim.io/dokumanlar/android) SDK'nın genel yüzeyini KİLİTLER.
 * Bu dosya dokümandaki kod bloklarını **birebir** derler ve çağırır; bir ad değişirse burada
 * kırılır. Yüzey değişecekse önce doküman + ana depodaki `test/mobile-s3.mjs`, sonra burası.
 */
@RunWith(RobolectricTestRunner::class)
class DocSurfaceTest {
    private val ctx: Context = RuntimeEnvironment.getApplication()
    private lateinit var api: FakeApi

    @Before fun setUp() {
        api = FakeApi(); Bildirim.resetForTests()
    }
    @After fun tearDown() { api.close(); Bildirim.resetForTests() }

    /** Doküman: "Kurulum" — iki satırlık init ve self-hosted varyantı. */
    @Test fun `kurulum imzalari`() {
        Bildirim.initialize(ctx, "pk_sizin_anahtariniz")
        Bildirim.resetForTests()
        Bildirim.initialize(ctx, "pk_...", BildirimConfig(apiBase = api.baseUrl))
        assertNotNull(Bildirim.runtimeForTests())
    }

    /** Doküman: "Bildirim nasıl çiziliyor" — kanal/simge/renk yapılandırması. */
    @Test fun `config alan adlari`() {
        val cfg = BildirimConfig(
            channelName = "Son dakika",
            smallIcon = android.R.drawable.ic_dialog_email,
            accentColor = android.R.color.holo_blue_dark,
        )
        assertEquals("Son dakika", cfg.channelName)
        assertEquals("bildirim_default", cfg.channelId)
        assertEquals(BildirimConfig.DEFAULT_API_BASE, cfg.apiBase)
    }

    /** Doküman: "İzin isteyin" — context'siz tek çağrı. */
    @Test fun `izin istegi tek cagri`() {
        Bildirim.initialize(ctx, "pk_test", BildirimConfig(apiBase = api.baseUrl, tokenProvider = FakeTokenProvider("t")))
        // 33+ izin verilmiş cihaz: tek çağrı doğrudan sonucu döndürür (sistem penceresi çıkmaz)
        org.robolectric.Shadows.shadowOf(ctx as android.app.Application)
            .grantPermissions(io.bildirim.sdk.internal.BildirimPermissionActivity.PERMISSION)
        var granted: Boolean? = null
        Bildirim.requestPermission { g -> granted = g }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(true, granted)
        assertTrue(Bildirim.areNotificationsEnabled())
    }

    /** Doküman: "Kullanıcıyı tanımlayın, etiketleyin, ölçün" — blok birebir. */
    @Test fun `kimlik etiket olay`() {
        Bildirim.initialize(ctx, "pk_test", BildirimConfig(apiBase = api.baseUrl, tokenProvider = FakeTokenProvider("tok")))
        Bildirim.login("kullanici-42")
        val imza = "b1a2c3"                                  // sunucunuzun ürettiği HMAC-SHA256 (hex)
        Bildirim.login("kullanici-42", identityHash = imza)  // proje "kimlik doğrulama" istiyorsa zorunlu
        Bildirim.logout()
        Bildirim.setTags(mapOf("sehir" to "istanbul", "plan" to "premium"))
        Bildirim.setTags(mapOf("plan" to null))
        Bildirim.track("satin_alma", mapOf("value" to 149.9, "currency" to "TRY"))
        Bildirim.unsubscribe()
        Bildirim.runtimeForTests()!!.sync.awaitIdle()

        val imzali = api.requests.first { it.path == "/v1/subscribe" && it.body!!.has("identityHash") }.body!!
        assertEquals("kullanici-42", imzali.getString("externalId"))
        assertEquals("b1a2c3", imzali.getString("identityHash"))
        val imzasiz = api.requests.first { it.path == "/v1/subscribe" && it.body!!.has("externalId") && !it.body!!.has("identityHash") }.body!!
        assertEquals("kullanici-42", imzasiz.getString("externalId"))

        val track = api.requests.last { it.path == "/v1/events" }.body!!
        assertEquals("satin_alma", track.getString("name"))
        assertEquals(149.9, track.getDouble("value"), 0.001)
        assertEquals("TRY", track.getString("currency"))
        assertFalse("value/currency properties içinde tekrar edilmemeli", track.has("properties"))
    }

    /** Doküman: "Tıklama ve deep link" + "ön planda" — handler'lar bildirim nesnesi alır. */
    @Test fun `handler imzalari ve bildirim alanlari`() {
        Bildirim.initialize(ctx, "pk_test", BildirimConfig(apiBase = api.baseUrl, tokenProvider = FakeTokenProvider("tok")))
        val aktifSayfaKampanyasi = "diger"
        Bildirim.setForegroundHandler { bildirim -> bildirim.campaignId != aktifSayfaKampanyasi }
        var seen: BildirimNotification? = null
        Bildirim.setNotificationOpenedHandler { bildirim ->
            seen = bildirim
            Intent().putExtra("url", bildirim.url)
            true
        }
        val n = Payload.parseRaw("""{"c":"k1","t":"t1","ti":"B","b":"G","u":"https://x/1","i":"https://x/g.jpg","ic":"https://x/i.png","a":[{"id":"oku","l":"Oku"}]}""")!!
        // alan adları: campaignId, url, actionId, title, body, image, icon
        assertEquals("k1", n.campaignId); assertEquals("https://x/1", n.url)
        assertEquals("B", n.title); assertEquals("G", n.body)
        assertEquals("https://x/g.jpg", n.image); assertEquals("https://x/i.png", n.icon)
        assertEquals(null, n.actionId)
        assertTrue(Bildirim.internalDispatchOpened(n.copy(actionId = "oku")))
        assertEquals("oku", seen!!.actionId)
    }

    /** Doküman: "Kendi FirebaseMessagingService'im var" — iki köprü ve manifest adı. */
    @Test fun `kendi servisi olan uygulama koprulari`() {
        Bildirim.initialize(ctx, "pk_test", BildirimConfig(apiBase = api.baseUrl, tokenProvider = FakeTokenProvider(null)))
        Bildirim.onNewToken("yeni-jeton")
        Bildirim.runtimeForTests()!!.sync.awaitIdle()
        assertEquals("yeni-jeton", Bildirim.getToken())

        // Bildirim'e ait olmayan mesaja dokunmaz
        assertFalse(Bildirim.onMessageReceived(mapOf("baska" to "veri")))
        assertTrue(Bildirim.onMessageReceived(mapOf("bildirim" to """{"c":"k","t":"t"}""")))
    }

    /** Manifest'teki servis adı dokümandaki `tools:node="remove"` satırıyla aynı olmalı. */
    @Test fun `manifest servis adi`() {
        val manifest = java.io.File("src/main/AndroidManifest.xml").let {
            if (it.exists()) it else java.io.File("bildirim/src/main/AndroidManifest.xml")
        }.readText()
        assertTrue(
            "Doküman io.bildirim.sdk.internal.MessagingService adını yazıyor",
            manifest.contains("io.bildirim.sdk.internal.MessagingService"),
        )
    }

    /** Sürüm dokümandaki ve sözleşmedeki sürümle aynı olmalı. */
    @Test fun `surum sozlesmedeki sdk bloguyla ayni`() {
        val sdk = contractJson().getJSONObject("sdk").getJSONObject("android")
        assertEquals(BildirimVersion.SDK_VERSION, sdk.getString("latest"))
        assertEquals("io.bildirim:bildirim-android", sdk.getString("artifact"))
        assertEquals(21, sdk.getInt("minSdk"))
    }
}

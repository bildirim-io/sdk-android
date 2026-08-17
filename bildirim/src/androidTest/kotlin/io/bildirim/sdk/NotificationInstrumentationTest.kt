package io.bildirim.sdk

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import io.bildirim.sdk.internal.NotificationRenderer
import io.bildirim.sdk.internal.Payload
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Gerçek Android'de (emülatör/cihaz) çizim doğrulaması — Robolectric'in göstermediği katman:
 * kanalın sistemde gerçekten oluşması, bildirimin gerçekten aktif bildirimler arasına düşmesi,
 * BigPicture/aksiyon yapısının platform tarafından kabul edilmesi.
 *
 * `./gradlew :bildirim:connectedDebugAndroidTest` (Play imajlı emülatör gerekmez; FCM'e dokunmuyor).
 */
@RunWith(AndroidJUnit4::class)
class NotificationInstrumentationTest {
    /**
     * Android 13+ izin olmadan `notify()` **sessizce düşer** — testin ilk turunda tam bu yüzden
     * hiçbir bildirim görünmedi. İzni testte açıkça veriyoruz; SDK da aynı kapıyı kontrol edip
     * izin yoksa "gösterildi" olayı göndermiyor.
     */
    @get:Rule
    val izin: GrantPermissionRule = if (Build.VERSION.SDK_INT >= 33) {
        GrantPermissionRule.grant("android.permission.POST_NOTIFICATIONS")
    } else {
        GrantPermissionRule.grant()
    }

    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val nm: NotificationManager
        get() = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val raw = """{"c":"enstrumantasyon","t":"tok","ti":"Başlık","b":"Gövde",""" +
        """"u":"https://bildirim.io","a":[{"id":"oku","l":"Oku"},{"id":"kaydet","l":"Sonra oku","u":"https://bildirim.io/x"}]}"""

    @Before fun setUp() = nm.cancelAll()
    @After fun tearDown() = nm.cancelAll()

    @Test fun kanalOlusturulur() {
        NotificationRenderer(ctx, BildirimConfig()).ensureChannel()
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = nm.getNotificationChannel("bildirim_default")
            assertNotNull("bildirim_default kanalı sistemde oluşmalı", ch)
            assertEquals(NotificationManager.IMPORTANCE_HIGH, ch!!.importance)
        }
    }

    @Test fun bildirimGercektenDuser() {
        val n = Payload.parseRaw(raw)!!
        NotificationRenderer(ctx, BildirimConfig()).show(n)
        if (Build.VERSION.SDK_INT >= 23) {
            val active = nm.activeNotifications.filter { it.tag == "bildirim" }
            assertEquals(1, active.size)
            val notif = active[0].notification
            assertEquals("bildirim_default", if (Build.VERSION.SDK_INT >= 26) notif.channelId else "bildirim_default")
            assertEquals(2, notif.actions.size)
            assertEquals("Oku", notif.actions[0].title.toString())
            assertNotNull(notif.contentIntent)
            assertNotNull(notif.deleteIntent)
        }
    }

    @Test fun ikinciYayimAyniBildirimiGunceller() {
        val n = Payload.parseRaw(raw)!!
        val r = NotificationRenderer(ctx, BildirimConfig())
        r.show(n)
        r.show(n) // görsel indikten sonraki ikinci faz
        if (Build.VERSION.SDK_INT >= 23) {
            assertEquals(1, nm.activeNotifications.count { it.tag == "bildirim" })
        }
    }

    @Test fun izinVerilmisCihazdaBildirimlerAcik() {
        Bildirim.resetForTests()
        Bildirim.initialize(ctx, "pk_enstrumantasyon", BildirimConfig(tokenProvider = TokenProvider { it(null) }))
        assertTrue("izin verilmiş cihazda açık görünmeli", Bildirim.areNotificationsEnabled())
        assertNotNull(Bildirim.getInstallationId())
    }
}

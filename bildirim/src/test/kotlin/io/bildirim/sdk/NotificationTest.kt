package io.bildirim.sdk

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import io.bildirim.sdk.internal.BildirimClickActivity
import io.bildirim.sdk.internal.NotificationRenderer
import io.bildirim.sdk.internal.Payload
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class NotificationTest {
    private val ctx: Context = RuntimeEnvironment.getApplication()
    private lateinit var api: FakeApi

    @Before fun setUp() {
        api = FakeApi(); Bildirim.resetForTests()
        Bildirim.initialize(ctx, BildirimConfig(appKey = "pk_test", apiBase = api.baseUrl, tokenProvider = FakeTokenProvider("tok-1")))
        Bildirim.runtimeForTests()!!.sync.awaitIdle()
        api.requests.clear()
    }
    @After fun tearDown() { api.close(); Bildirim.resetForTests() }

    private val raw = """{"c":"kamp1","t":"tok-olay","ti":"Başlık","b":"Gövde","u":"https://ornek.com/haber/1","a":[{"id":"oku","l":"Oku"},{"id":"kaydet","l":"Sonra oku","u":"https://ornek.com/kaydet/1"}]}"""

    @Test fun `bildirim cizilir - kanal, aksiyonlar, intentler`() {
        val n = Payload.parseRaw(raw)!!
        val r = NotificationRenderer(ctx, BildirimConfig(appKey = "pk_test"))
        r.show(n)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val all = shadowOf(nm).allNotifications
        assertEquals(1, all.size)
        val notif: Notification = all[0]
        assertEquals("bildirim_default", notif.channelId)
        assertNotNull(nm.getNotificationChannel("bildirim_default"))
        assertEquals(2, notif.actions.size)
        assertEquals("Oku", notif.actions[0].title.toString())
        assertNotNull(notif.contentIntent)
        assertNotNull(notif.deleteIntent)
        assertTrue(notif.flags and Notification.FLAG_AUTO_CANCEL != 0)
        // aksiyon PendingIntent'leri farklı istek kodlarında → farklı olmalı
        assertTrue(notif.actions[0].actionIntent != notif.actions[1].actionIntent)
    }

    @Test fun `handleRemoteMessage yabanci mesaja dokunmaz, bizimkini cizer ve displayed gonderir`() {
        assertEquals(false, Bildirim.handleRemoteMessage(ctx, mapOf("x" to "y")))
        assertEquals(true, Bildirim.handleRemoteMessage(ctx, mapOf("bildirim" to raw)))
        Bildirim.runtimeForTests()!!.sync.awaitIdle()
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertEquals(1, shadowOf(nm).allNotifications.size)
        assertEquals("/v1/mobile/events", api.paths().last())
        val b = api.requests.last().body!!
        assertEquals("displayed", b.getString("event")); assertEquals("tok-olay", b.getString("t"))
    }

    @Test fun `tiklama - clicked olayi, actionId ve adres acma`() {
        val n = Payload.parseRaw(raw)!!
        val intent = Intent(ctx, BildirimClickActivity::class.java)
            .putExtra(BildirimClickActivity.EXTRA_RAW, n.raw)
            .putExtra(BildirimClickActivity.EXTRA_ACTION_ID, "kaydet")
            .putExtra(BildirimClickActivity.EXTRA_NOTIFICATION_ID, n.notificationId)
        var opened: BildirimOpenResult? = null
        Bildirim.setNotificationOpenedHandler { r -> opened = r; false }
        val controller = Robolectric.buildActivity(BildirimClickActivity::class.java, intent).create()
        assertTrue(controller.get().isFinishing)
        Bildirim.runtimeForTests()!!.sync.awaitIdle()

        assertNotNull(opened)
        assertEquals("kaydet", opened!!.actionId)
        assertEquals("https://ornek.com/kaydet/1", opened!!.url)
        assertEquals("kamp1", opened!!.notification.campaignId)

        val ev = api.requests.last { it.path == "/v1/mobile/events" }.body!!
        assertEquals("clicked", ev.getString("event")); assertEquals("kaydet", ev.getString("actionId")); assertEquals("tok-olay", ev.getString("t"))

        val started = shadowOf(ctx as Application).nextStartedActivity
        assertNotNull(started)
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("https://ornek.com/kaydet/1", started.dataString)
    }

    @Test fun `handler ustlenirse SDK adres acmaz`() {
        val n = Payload.parseRaw(raw)!!
        val intent = Intent(ctx, BildirimClickActivity::class.java).putExtra(BildirimClickActivity.EXTRA_RAW, n.raw)
        Bildirim.setNotificationOpenedHandler { true }
        Robolectric.buildActivity(BildirimClickActivity::class.java, intent).create()
        assertNull(shadowOf(ctx as Application).nextStartedActivity)
        Bildirim.runtimeForTests()!!.sync.awaitIdle()
        val ev = api.requests.last { it.path == "/v1/mobile/events" }.body!!
        assertEquals("clicked", ev.getString("event")); assertTrue(!ev.has("actionId"))
    }

    @Test fun `on planda handler false derse cizilmez ve displayed gitmez`() {
        val rt = Bildirim.runtimeForTests()!!
        // ön planı simüle et
        val act = Robolectric.buildActivity(android.app.Activity::class.java).create().start()
        rt.lifecycle.onActivityStarted(act.get())
        Bildirim.setForegroundHandler { false }
        assertEquals(true, Bildirim.handleRemoteMessage(ctx, mapOf("bildirim" to raw)))
        rt.sync.awaitIdle()
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertEquals(0, shadowOf(nm).allNotifications.size)
        assertTrue(api.paths().none { it == "/v1/mobile/events" })
    }
}

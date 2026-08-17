package io.bildirim.sdk

import io.bildirim.sdk.internal.Contract
import io.bildirim.sdk.internal.Payload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Vendored contracts/mobile-sdk.json ile koddaki sabitlerin hizası. Sunucu sözleşmeyi
 * değiştirdiğinde (yeni alan, yeni sürüm) önce bu test kırılır — istenen budur.
 */
@RunWith(RobolectricTestRunner::class)
class ContractTest {
    private val c = contractJson()

    @Test fun `surum ayni`() {
        assertEquals(Contract.VERSION, c.getInt("version"))
        assertEquals(Contract.PAYLOAD_KEY, c.getString("payloadKey"))
    }

    @Test fun `yuk alanlari ayni`() {
        val fields = c.getJSONObject("fields").keys().asSequence().toSet()
        assertEquals(fields, Contract.PAYLOAD_FIELDS)
    }

    @Test fun `uc yollari ayni`() {
        val e = c.getJSONObject("endpoints")
        assertEquals(Contract.PATH_SUBSCRIBE, e.getJSONObject("subscribe").getString("path"))
        assertEquals(Contract.PATH_UNSUBSCRIBE, e.getJSONObject("unsubscribe").getString("path"))
        assertEquals(Contract.PATH_NOTIFICATION_EVENT, e.getJSONObject("notificationEvent").getString("path"))
        assertEquals(Contract.PATH_TRACK, e.getJSONObject("track").getString("path"))
        assertTrue(e.getJSONObject("config").getString("path").startsWith(Contract.PATH_CONFIG))
        assertEquals(Contract.PATH_CONTRACT, e.getJSONObject("contract").getString("path"))
        val events = e.getJSONObject("notificationEvent").getJSONArray("events")
        val names = (0 until events.length()).map { events.getString(it) }.toSet()
        assertEquals(setOf(Contract.EV_DISPLAYED, Contract.EV_CLICKED, Contract.EV_DISMISSED), names)
    }

    @Test fun `subscribe govdesi sozlesmedeki alanlari kapsiyor`() {
        val e = c.getJSONObject("endpoints").getJSONObject("subscribe").getJSONArray("body")
        val fields = (0 until e.length()).map { e.getString(it) }.toSet()
        // apnsEnvironment yalnız iOS; geri kalan hepsi Android gövdesinde
        val expected = fields - "apnsEnvironment"
        val ours = setOf("projectKey", "channel", "token", "installationId", "appVersion", "sdkVersion", "os", "timezone", "externalId", "tags", "country")
        assertEquals(expected, ours)
    }

    @Test fun `fcm ornek yuku cozumleniyor`() {
        val data = c.getJSONObject("fcm").getJSONObject("example").getJSONObject("message").getJSONObject("data")
        val map = mapOf(Contract.PAYLOAD_KEY to data.getString(Contract.PAYLOAD_KEY))
        val n = Payload.parse(map)
        assertNotNull(n)
        n!!
        assertEquals("Başlık", n.title)
        assertEquals("Gövde", n.body)
        assertEquals("https://ornek.com/haber/1", n.url)
        assertEquals("https://ornek.com/g.jpg", n.imageUrl)
        assertNotNull(n.campaignId); assertNotNull(n.eventToken)
        assertEquals(2, n.actions.size)
        assertEquals("oku", n.actions[0].id); assertEquals("Oku", n.actions[0].label); assertEquals(null, n.actions[0].url)
        assertEquals("kaydet", n.actions[1].id); assertEquals("https://ornek.com/kaydet/1", n.actions[1].url)
    }

    @Test fun `sdk surumu gradle properties ile ayni`() {
        val props = listOf(File("../gradle.properties"), File("gradle.properties")).first { it.exists() }.readLines()
        val v = props.first { it.startsWith("VERSION_NAME=") }.substringAfter("=").trim()
        assertEquals(v, BildirimVersion.SDK_VERSION)
    }
}

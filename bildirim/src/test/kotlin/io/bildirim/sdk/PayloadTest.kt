package io.bildirim.sdk

import io.bildirim.sdk.internal.Payload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PayloadTest {

    @Test fun `bildirim anahtari yoksa dokunma`() {
        assertFalse(Payload.isBildirim(mapOf("foo" to "bar")))
        assertNull(Payload.parse(mapOf("foo" to "bar")))
    }

    @Test fun `bozuk json null`() {
        assertTrue(Payload.isBildirim(mapOf("bildirim" to "{bozuk")))
        assertNull(Payload.parse(mapOf("bildirim" to "{bozuk")))
    }

    @Test fun `asgari yuk`() {
        val n = Payload.parse(mapOf("bildirim" to """{"c":"k1","t":"tok","ti":"Selam"}"""))!!
        assertEquals("k1", n.campaignId); assertEquals("tok", n.eventToken); assertEquals("Selam", n.title)
        assertNull(n.body); assertNull(n.url); assertNull(n.imageUrl); assertNull(n.iconUrl)
        assertTrue(n.actions.isEmpty())
    }

    @Test fun `null ve bos degerler yok sayilir`() {
        val n = Payload.parse(mapOf("bildirim" to """{"c":"k1","u":null,"i":"","b":"x"}"""))!!
        assertNull(n.url); assertNull(n.imageUrl); assertEquals("x", n.body)
    }

    @Test fun `aksiyonlar en cok uc ve eksik alanli olanlar atlanir`() {
        val raw = """{"c":"k","a":[{"id":"a1","l":"A"},{"id":"a2"},{"l":"C"},{"id":"a3","l":"C","u":"https://x"},{"id":"a4","l":"D"},{"id":"a5","l":"E"}]}"""
        val n = Payload.parse(mapOf("bildirim" to raw))!!
        assertEquals(listOf("a1", "a3", "a4"), n.actions.map { it.id })
        assertEquals("https://x", n.actions[1].url)
    }

    @Test fun `bildirim kimligi kampanyaya bagli`() {
        val a = Payload.parseRaw("""{"c":"k1","t":"t1"}""")!!
        val b = Payload.parseRaw("""{"c":"k1","t":"t2"}""")!!
        val c = Payload.parseRaw("""{"c":"k2","t":"t1"}""")!!
        assertEquals(a.notificationId, b.notificationId)
        assertNotNull(c.notificationId)
        assertTrue(a.notificationId != c.notificationId)
    }
}

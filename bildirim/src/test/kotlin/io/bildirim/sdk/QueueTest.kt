package io.bildirim.sdk

import org.robolectric.RuntimeEnvironment
import android.content.Context
import io.bildirim.sdk.internal.Queue
import io.bildirim.sdk.internal.Store
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QueueTest {
    private val ctx: Context = RuntimeEnvironment.getApplication()

    @Test fun `fifo ve kalicilik`() {
        val store = Store(ctx)
        val q = Queue(store)
        q.add(Queue.KIND_SUBSCRIBE, JSONObject().put("externalId", "u1"))
        q.add(Queue.KIND_TRACK, JSONObject().put("name", "a"))
        q.add(Queue.KIND_SUBSCRIBE, JSONObject().put("externalId", JSONObject.NULL))
        q.add(Queue.KIND_SUBSCRIBE, JSONObject().put("tags", JSONObject().put("x", 1)))

        // yeniden yükle (süreç yeniden başlamış gibi)
        val q2 = Queue(Store(ctx))
        assertEquals(4, q2.size())
        val kinds = q2.snapshot().map { it.kind }
        assertEquals(listOf("subscribe", "track", "subscribe", "subscribe"), kinds)
        // logout (externalId null) setTags'ten ÖNCE — sıra korunur
        val items = q2.snapshot()
        assertEquals(true, items[2].delta.isNull("externalId"))
        assertEquals(1, items[3].delta.getJSONObject("tags").getInt("x"))

        q2.remove(items[0].id)
        assertEquals("track", q2.peek()!!.kind)
        assertEquals(3, Queue(Store(ctx)).size())
    }

    @Test fun `addFirst basa ekler`() {
        val q = Queue(Store(ctx))
        q.add(Queue.KIND_TRACK, JSONObject())
        q.addFirst(Queue.KIND_SUBSCRIBE, JSONObject())
        assertEquals("subscribe", q.peek()!!.kind)
        assertEquals(listOf("subscribe", "track"), q.snapshot().map { it.kind })
    }

    @Test fun `tavan asilinca en eski duser`() {
        val q = Queue(Store(ctx))
        repeat(Queue.CAP + 5) { q.add(Queue.KIND_TRACK, JSONObject().put("n", it)) }
        assertEquals(Queue.CAP, q.size())
        assertEquals(5, q.peek()!!.delta.getInt("n"))
    }

    @Test fun `bos kuyruk`() {
        val q = Queue(Store(ctx))
        assertNull(q.peek())
        assertEquals(0, q.size())
    }
}

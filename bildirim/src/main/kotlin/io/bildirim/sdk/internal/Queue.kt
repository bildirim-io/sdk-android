package io.bildirim.sdk.internal

import org.json.JSONArray
import org.json.JSONObject

/**
 * Sıralı, kalıcı çevrimdışı kuyruk. Öğeler yalnız *delta* taşır (login → {externalId},
 * setTags → {tags}, track → {name,…}, event → {t,event,actionId}); jeton, kurulum kimliği,
 * sürümler gibi ortak alanlar gönderim anında eklenir. Böylece jeton kuyruktayken yenilense
 * bile doğru jetonla gider ve `logout` sonrası eski `setTags` yeni kullanıcıya yazılmaz:
 * öğeler eklendikleri sırayla, o anki değerleriyle işlenir.
 */
internal class Queue(private val store: Store) {

    class Item(val id: Long, val kind: String, val delta: JSONObject, val createdAt: Long) {
        fun toJson(): JSONObject = JSONObject()
            .put("id", id).put("kind", kind).put("delta", delta).put("createdAt", createdAt)

        companion object {
            fun from(o: JSONObject): Item? {
                val kind = o.optString("kind", "")
                if (kind.isEmpty()) return null
                return Item(o.optLong("id"), kind, o.optJSONObject("delta") ?: JSONObject(), o.optLong("createdAt"))
            }
        }
    }

    private val lock = Any()
    private var items: MutableList<Item> = load()

    private fun load(): MutableList<Item> {
        val out = ArrayList<Item>()
        try {
            val arr = JSONArray(store.queueJson)
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { Item.from(it) }?.let(out::add)
        } catch (e: Exception) {
            Log.w("kuyruk okunamadı, sıfırlanıyor: ${e.message}")
        }
        return out
    }

    private fun persist() {
        val arr = JSONArray()
        items.forEach { arr.put(it.toJson()) }
        store.queueJson = arr.toString()
    }

    fun add(kind: String, delta: JSONObject, now: Long = System.currentTimeMillis()): Item = synchronized(lock) {
        val id = (items.lastOrNull()?.id ?: 0L) + 1
        val item = Item(id, kind, delta, now)
        items.add(item)
        while (items.size > CAP) {
            val dropped = items.removeAt(0)
            Log.w("kuyruk dolu (${CAP}), en eski öğe düşürüldü: ${dropped.kind}")
        }
        persist()
        item
    }

    /** Başa ekler — yalnız "önce kayıt gitmeli" durumu için (bkz. SyncEngine.drain). */
    fun addFirst(kind: String, delta: JSONObject, now: Long = System.currentTimeMillis()): Item = synchronized(lock) {
        val id = (items.firstOrNull()?.id ?: 1L) - 1
        val item = Item(id, kind, delta, now)
        items.add(0, item)
        persist()
        item
    }

    fun peek(): Item? = synchronized(lock) { items.firstOrNull() }

    fun remove(id: Long) = synchronized(lock) {
        if (items.removeAll { it.id == id }) persist()
    }

    fun size(): Int = synchronized(lock) { items.size }

    fun any(kind: String): Boolean = synchronized(lock) { items.any { it.kind == kind } }

    fun snapshot(): List<Item> = synchronized(lock) { ArrayList(items) }

    fun clear() = synchronized(lock) { items.clear(); persist() }

    companion object {
        const val CAP = 500
        const val KIND_SUBSCRIBE = "subscribe"
        const val KIND_UNSUBSCRIBE = "unsubscribe"
        const val KIND_TRACK = "track"
        const val KIND_EVENT = "event"
    }
}

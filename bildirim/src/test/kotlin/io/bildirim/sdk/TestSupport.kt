package io.bildirim.sdk

import com.sun.net.httpserver.HttpServer
import org.json.JSONObject
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

/** Sahte jeton sağlayıcı. */
class FakeTokenProvider(private val token: String?) : TokenProvider {
    override fun fetch(callback: (String?) -> Unit) = callback(token)
}

/** Depodaki vendored sözleşme dosyası. Çalışma dizini modül kökü (bildirim/). */
fun contractFile(): File {
    val candidates = listOf(File("../contracts/mobile-sdk.json"), File("contracts/mobile-sdk.json"))
    return candidates.firstOrNull { it.exists() } ?: error("contracts/mobile-sdk.json bulunamadı (cwd=${File(".").absolutePath})")
}

fun contractJson(): JSONObject = JSONObject(contractFile().readText())

class Recorded(val method: String, val path: String, val body: JSONObject?)

/** JDK'nın kendi HttpServer'ı — MockWebServer bağımlılığı eklememek için. */
class FakeApi : AutoCloseable {
    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val requests = CopyOnWriteArrayList<Recorded>()
    /** path → (status, body, headers) — kuyruk gibi tüketilir; boşsa varsayılan 200/201. */
    private val responses = HashMap<String, ArrayDeque<Triple<Int, String, Map<String, String>>>>()

    val baseUrl: String get() = "http://127.0.0.1:${server.address.port}"

    /** true → her isteğe 503 (sunucu/ağ yokmuş gibi). */
    @Volatile var down: Boolean = false

    init {
        server.createContext("/") { ex ->
            val bodyText = ex.requestBody.readBytes().toString(Charsets.UTF_8)
            val body = if (bodyText.isNotBlank()) try { JSONObject(bodyText) } catch (_: Exception) { null } else null
            requests.add(Recorded(ex.requestMethod, ex.requestURI.path, body))
            val next = if (down) Triple(503, """{"error":"down"}""", emptyMap<String, String>())
                else synchronized(responses) { responses[ex.requestURI.path]?.removeFirstOrNull() }
            val (status, resp, headers) = next ?: defaultResponse(ex.requestURI.path)
            headers.forEach { (k, v) -> ex.responseHeaders.add(k, v) }
            ex.responseHeaders.add("Content-Type", "application/json")
            val bytes = resp.toByteArray(Charsets.UTF_8)
            ex.sendResponseHeaders(status, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    private fun defaultResponse(path: String): Triple<Int, String, Map<String, String>> = when (path) {
        "/v1/subscribe" -> Triple(201, """{"id":"sub_1","status":"subscribed"}""", emptyMap())
        "/v1/unsubscribe" -> Triple(200, """{"status":"unsubscribed"}""", emptyMap())
        "/v1/mobile/events" -> Triple(200, """{"status":"ok"}""", emptyMap())
        "/v1/events" -> Triple(201, """{"id":"ev_1","attributed":false}""", emptyMap())
        else -> Triple(404, """{"error":"not_found"}""", emptyMap())
    }

    fun enqueue(path: String, status: Int, body: String, headers: Map<String, String> = emptyMap()) {
        synchronized(responses) { responses.getOrPut(path) { ArrayDeque() }.addLast(Triple(status, body, headers)) }
    }

    fun paths(): List<String> = requests.map { it.path }

    override fun close() = server.stop(0)
}

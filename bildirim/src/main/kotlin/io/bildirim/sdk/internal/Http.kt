package io.bildirim.sdk.internal

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Bağımlılıksız JSON HTTP istemcisi (HttpURLConnection). Sonuç dört sınıfa indirgenir; kuyruk
 * yalnız buna bakarak "sil / bekle / dur" kararı verir.
 */
internal object Http {

    sealed class Result {
        /** 2xx */
        class Ok(val status: Int, val body: JSONObject?) : Result()
        /** 429 ya da 5xx — bekleyip tekrar denenir. [retryAfterMs] Retry-After başlığından. */
        class Retry(val status: Int, val retryAfterMs: Long?) : Result()
        /** Diğer 4xx — kalıcı hata, öğe düşürülür. */
        class Drop(val status: Int, val body: JSONObject?) : Result()
        /** Ağ hatası / zaman aşımı — tekrar denenir. */
        class NetworkError(val error: IOException) : Result()
    }

    var connectTimeoutMs = 10_000
    var readTimeoutMs = 15_000

    fun postJson(url: String, body: JSONObject, userAgent: String): Result = request("POST", url, body, userAgent)

    fun getJson(url: String, userAgent: String): Result = request("GET", url, null, userAgent)

    private fun request(method: String, url: String, body: JSONObject?, userAgent: String): Result {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", userAgent)
                // Origin başlığı bilerek GÖNDERİLMEZ — mobil kanallarda aranmaz.
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
            }
            val status = conn.responseCode
            val stream = if (status >= 400) conn.errorStream else conn.inputStream
            val text = stream?.let(::readAll) ?: ""
            val json = if (text.isNotBlank()) try { JSONObject(text) } catch (_: Exception) { null } else null
            Log.d("$method $url → $status")
            return when {
                status in 200..299 -> Result.Ok(status, json)
                status == 429 || status >= 500 -> Result.Retry(status, parseRetryAfter(conn.getHeaderField("Retry-After")))
                else -> Result.Drop(status, json)
            }
        } catch (e: IOException) {
            return Result.NetworkError(e)
        } finally {
            conn?.disconnect()
        }
    }

    private fun readAll(input: InputStream): String {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        input.use { s ->
            while (true) {
                val n = s.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > 1_000_000) break
            }
        }
        return out.toString("UTF-8")
    }

    private fun parseRetryAfter(v: String?): Long? {
        val s = v?.trim() ?: return null
        s.toLongOrNull()?.let { return it * 1000 }
        return null // HTTP-date biçimi desteklenmiyor; backoff devreye girer
    }
}

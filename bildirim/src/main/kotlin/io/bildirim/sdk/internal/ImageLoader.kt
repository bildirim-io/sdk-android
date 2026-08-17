package io.bildirim.sdk.internal

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Bağımlılıksız görsel indirici. Hata → null (bildirim görselsiz çizilir). */
internal object ImageLoader {
    private const val MAX_BYTES = 2 * 1024 * 1024
    private const val MAX_DIM = 1024

    fun load(url: String, timeoutMs: Int = 5_000): Bitmap? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = true
                setRequestProperty("Accept", "image/*")
            }
            if (conn.responseCode !in 200..299) return null
            val bytes = conn.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_BYTES) { Log.w("görsel 2 MB sınırını aştı, atlandı: $url"); return null }
                }
                out.toByteArray()
            }
            decode(bytes)
        } catch (e: Exception) {
            Log.d("görsel indirilemedi ($url): ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    internal fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > MAX_DIM || bounds.outHeight / sample > MAX_DIM) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }
}

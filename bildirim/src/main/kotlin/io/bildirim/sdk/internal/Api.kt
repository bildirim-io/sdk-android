package io.bildirim.sdk.internal

import org.json.JSONObject

/**
 * Sözleşmedeki uçların gövde kurucuları ve çağrıları. Alan adları Contract'tan; burada
 * elle dizge yazılmaz.
 */
internal class Api(private val apiBase: String, private val appKey: String, private val device: DeviceInfo) {

    private fun base(token: String): JSONObject = JSONObject()
        .put("projectKey", appKey)
        .put("channel", Contract.CHANNEL)
        .put("token", token)

    /**
     * Kayıt/güncelleme gövdesi. [delta] `externalId` (JSONObject.NULL → logout) ve/veya `tags`
     * (değeri JSONObject.NULL olan anahtar sunucuda silinir) içerebilir.
     */
    fun subscribeBody(token: String, installationId: String, delta: JSONObject?): JSONObject {
        val b = base(token)
            .put("installationId", installationId)
            .put("appVersion", device.appVersion)
            .put("sdkVersion", device.sdkVersion)
            .put("os", Contract.OS)
            .put("timezone", device.timezone)
        device.country?.let { b.put("country", it) }
        if (delta != null) {
            if (delta.has("externalId")) b.put("externalId", if (delta.isNull("externalId")) JSONObject.NULL else delta.get("externalId"))
            if (delta.has("tags")) b.put("tags", delta.get("tags"))
        }
        return b
    }

    fun unsubscribeBody(token: String): JSONObject = base(token)

    fun trackBody(token: String, delta: JSONObject): JSONObject {
        val b = base(token).put("name", delta.optString("name"))
        if (delta.has("value") && !delta.isNull("value")) b.put("value", delta.get("value"))
        if (delta.has("currency") && !delta.isNull("currency")) b.put("currency", delta.get("currency"))
        if (delta.has("properties") && !delta.isNull("properties")) b.put("properties", delta.get("properties"))
        return b
    }

    fun eventBody(token: String, delta: JSONObject): JSONObject {
        val b = base(token)
            .put("t", delta.optString("t"))
            .put("event", delta.optString("event"))
        if (delta.has("actionId") && !delta.isNull("actionId")) b.put("actionId", delta.get("actionId"))
        return b
    }

    fun subscribe(body: JSONObject) = Http.postJson(apiBase + Contract.PATH_SUBSCRIBE, body, device.userAgent)
    fun unsubscribe(body: JSONObject) = Http.postJson(apiBase + Contract.PATH_UNSUBSCRIBE, body, device.userAgent)
    fun track(body: JSONObject) = Http.postJson(apiBase + Contract.PATH_TRACK, body, device.userAgent)
    fun notificationEvent(body: JSONObject) = Http.postJson(apiBase + Contract.PATH_NOTIFICATION_EVENT, body, device.userAgent)
    fun config() = Http.getJson("$apiBase${Contract.PATH_CONFIG}?key=$appKey&channel=${Contract.CHANNEL}", device.userAgent)
}

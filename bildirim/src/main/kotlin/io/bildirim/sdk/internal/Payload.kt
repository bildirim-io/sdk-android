package io.bildirim.sdk.internal

import io.bildirim.sdk.BildirimAction
import io.bildirim.sdk.BildirimNotification
import org.json.JSONObject

/** FCM `data` haritasından Bildirim yükünü çözer. Bildirim'e ait değilse ya da bozuksa null. */
internal object Payload {

    fun isBildirim(data: Map<String, String>): Boolean = data.containsKey(Contract.PAYLOAD_KEY)

    fun parse(data: Map<String, String>): BildirimNotification? {
        val raw = data[Contract.PAYLOAD_KEY] ?: return null
        return parseRaw(raw)
    }

    fun parseRaw(raw: String): BildirimNotification? {
        val obj = try { JSONObject(raw) } catch (e: Exception) {
            Log.w("bildirim yükü çözülemedi: ${e.message}")
            return null
        }
        val actions = ArrayList<BildirimAction>(Contract.MAX_ACTIONS)
        obj.optJSONArray(Contract.F_ACTIONS)?.let { arr ->
            for (i in 0 until arr.length()) {
                if (actions.size >= Contract.MAX_ACTIONS) break
                val a = arr.optJSONObject(i) ?: continue
                val id = a.str(Contract.F_ACTION_ID) ?: continue
                val label = a.str(Contract.F_ACTION_LABEL) ?: continue
                actions.add(BildirimAction(id, label, a.str(Contract.F_ACTION_URL)))
            }
        }
        return BildirimNotification(
            campaignId = obj.str(Contract.F_CAMPAIGN),
            title = obj.str(Contract.F_TITLE),
            body = obj.str(Contract.F_BODY),
            url = obj.str(Contract.F_URL),
            image = obj.str(Contract.F_IMAGE),
            icon = obj.str(Contract.F_ICON),
            actions = actions,
            eventToken = obj.str(Contract.F_TOKEN),
            raw = raw,
        )
    }

    /** optString "null" dizgesi döndürür; isNull ile boşları ele. */
    private fun JSONObject.str(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val v = optString(key, "")
        return v.ifEmpty { null }
    }
}

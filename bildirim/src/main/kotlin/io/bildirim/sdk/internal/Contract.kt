package io.bildirim.sdk.internal

/**
 * SDK ↔ sunucu sözleşmesi sabitleri. Kaynak: contracts/mobile-sdk.json (ana depoda
 * apps/api/src/lib/mobile-contract.ts). ContractTest vendored dosya ile buradaki
 * sürüm ve alan adlarını karşılaştırır — bir taraf değişince test kırılır; istenen budur.
 */
internal object Contract {
    const val VERSION = 2

    /** FCM `data` içindeki JSON dizgesinin anahtarı. */
    const val PAYLOAD_KEY = "bildirim"

    // bildirim sözlüğü
    const val F_CAMPAIGN = "c"
    const val F_TOKEN = "t"
    const val F_URL = "u"
    const val F_IMAGE = "i"
    const val F_ICON = "ic"
    const val F_TITLE = "ti"
    const val F_BODY = "b"
    const val F_ACTIONS = "a"
    const val F_ACTION_ID = "id"
    const val F_ACTION_LABEL = "l"
    const val F_ACTION_URL = "u"
    const val MAX_ACTIONS = 3

    /** Yükte tanımlı tüm alanlar (ContractTest bunu dosyadaki `fields` ile karşılaştırır). */
    val PAYLOAD_FIELDS = setOf(F_CAMPAIGN, F_TOKEN, F_URL, F_IMAGE, F_ICON, F_TITLE, F_BODY, F_ACTIONS)

    // uçlar
    const val PATH_SUBSCRIBE = "/v1/subscribe"
    const val PATH_UNSUBSCRIBE = "/v1/unsubscribe"
    const val PATH_NOTIFICATION_EVENT = "/v1/mobile/events"
    const val PATH_TRACK = "/v1/events"
    const val PATH_CONFIG = "/v1/mobile/config"
    const val PATH_CONTRACT = "/v1/mobile/contract"

    const val CHANNEL = "android"
    const val OS = "android"

    // olay adları
    const val EV_DISPLAYED = "displayed"
    const val EV_CLICKED = "clicked"
    const val EV_DISMISSED = "dismissed"

    /** Kayıt tazeleme aralığı (§3.3): son başarılı kayıttan bu kadar geçtiyse yeniden gönderilir. */
    const val REREGISTER_INTERVAL_MS = 24L * 60 * 60 * 1000
}

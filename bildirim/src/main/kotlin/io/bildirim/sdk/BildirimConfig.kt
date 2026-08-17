package io.bildirim.sdk

/**
 * SDK yapılandırması. Yalnız [appKey] zorunlu; diğerleri makul varsayılanlarla gelir.
 *
 * ```kotlin
 * Bildirim.initialize(this, BildirimConfig(appKey = "pk_..."))
 * ```
 */
public class BildirimConfig(
    /** Panel → Ayarlar → Anahtarlar'daki genel anahtar (`pk_…`). */
    public val appKey: String,
    /** API adresi. Self-hosted kurulumda değiştirin. Sonundaki eğik çizgi kaldırılır. */
    apiBase: String = DEFAULT_API_BASE,
    /** Bildirim kanalı kimliği (Android 8+). Değiştirirseniz eski kanal kullanıcı ayarlarında kalır. */
    public val channelId: String = "bildirim_default",
    /** Kanalın kullanıcıya görünen adı (Ayarlar → Bildirimler). */
    public val channelName: CharSequence = "Bildirimler",
    /**
     * Durum çubuğundaki küçük ikon (drawable kaynağı). Verilmezse manifest'teki
     * `io.bildirim.sdk.small_icon` meta-data'sına, o da yoksa uygulama ikonuna düşer.
     */
    public val smallIconRes: Int = 0,
    /** Bildirim vurgu rengi (ARGB). 0 → sistem varsayılanı. */
    public val accentColor: Int = 0,
    /** Log seviyesi: [LOG_NONE], [LOG_ERROR], [LOG_INFO], [LOG_DEBUG]. */
    public val logLevel: Int = LOG_INFO,
    /**
     * Ön plandayken bildirim çizilsin mi (handler verilmediyse). Varsayılan true: FCM data-only
     * mesajlarda sistem hiçbir şey çizmez; false yaparsanız ön planda bildirim görünmez.
     */
    public val showInForeground: Boolean = true,
    /** Jeton kaynağı. Varsayılan Firebase; yalnız test/özel kurulum için değiştirin. */
    public val tokenProvider: TokenProvider? = null,
) {
    public val apiBase: String = apiBase.trimEnd('/')

    public companion object {
        public const val DEFAULT_API_BASE: String = "https://api.bildirim.io"
        public const val LOG_NONE: Int = 0
        public const val LOG_ERROR: Int = 1
        public const val LOG_INFO: Int = 2
        public const val LOG_DEBUG: Int = 3
    }
}

package io.bildirim.sdk

/**
 * SDK yapılandırması (isteğe bağlı). Anahtar [Bildirim.initialize]'a ayrı verilir.
 *
 * ```kotlin
 * Bildirim.initialize(this, "pk_...", BildirimConfig(
 *     channelName = "Son dakika",
 *     smallIcon = R.drawable.ic_stat_bildirim,
 *     accentColor = R.color.marka,
 * ))
 * ```
 */
public class BildirimConfig(
    /** API adresi. Kendi sunucunuzda çalışan Bildirim için değiştirin. */
    apiBase: String = DEFAULT_API_BASE,
    /** Bildirim kanalı kimliği (Android 8+). Değiştirirseniz eski kanal kullanıcı ayarlarında kalır. */
    public val channelId: String = "bildirim_default",
    /** Kanalın kullanıcıya görünen adı (Ayarlar → Bildirimler). */
    public val channelName: CharSequence = "Bildirimler",
    /**
     * Durum çubuğundaki tek renkli küçük simge (drawable kaynağı, ör. `R.drawable.ic_stat_bildirim`).
     * Verilmezse manifest'teki `io.bildirim.sdk.small_icon` meta-data'sı, o da yoksa uygulama simgesi
     * kullanılır — bazı cihazlarda beyaz kare görünür, bir simge çizip vermeniz önerilir.
     */
    public val smallIcon: Int = 0,
    /** Bildirim vurgu rengi — **renk kaynağı** (ör. `R.color.marka`). 0 → sistem varsayılanı. */
    public val accentColor: Int = 0,
    /** Log seviyesi: [LOG_NONE], [LOG_ERROR], [LOG_INFO], [LOG_DEBUG]. */
    public val logLevel: Int = LOG_INFO,
    /**
     * Ön plandayken bildirim çizilsin mi (handler verilmediyse). Varsayılan true: FCM data-only
     * mesajlarda sistem hiçbir şey çizmez.
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

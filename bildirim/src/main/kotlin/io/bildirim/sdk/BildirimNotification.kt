package io.bildirim.sdk

/** Bildirimdeki bir aksiyon düğmesi. `url` yoksa düğme bildirimin kendi adresini açar. */
public data class BildirimAction(
    val id: String,
    val label: String,
    val url: String?,
)

/**
 * Bir Bildirim mesajının çözümlenmiş hâli — handler'lara bu verilir.
 *
 * ```kotlin
 * Bildirim.setNotificationOpenedHandler { bildirim ->
 *     startActivity(Intent(this, HaberActivity::class.java).putExtra("url", bildirim.url))
 *     true
 * }
 * ```
 */
public data class BildirimNotification(
    /** Kampanya kimliği (sunucudaki `c`). */
    val campaignId: String?,
    val title: String?,
    val body: String?,
    /** Tıklanınca açılacak adres; aksiyon düğmesine basıldıysa düğmenin adresi (yoksa bildirimin). */
    val url: String?,
    /** Büyük görsel adresi. */
    val image: String?,
    /** Bildirim simgesi adresi. */
    val icon: String?,
    /** Basılan aksiyon düğmesinin kimliği — yalnız tıklama handler'ında ve yalnız düğmeye basıldıysa. */
    val actionId: String? = null,
    /** Bildirimdeki aksiyon düğmeleri (en çok 3). */
    val actions: List<BildirimAction> = emptyList(),
    /** Ölçüm jetonu (sunucudaki `t`) — SDK kullanır. */
    val eventToken: String? = null,
    /** Ham `bildirim` JSON dizgesi — ileride eklenecek alanlar için. */
    val raw: String = "",
) {
    /** Bildirim ve aksiyonlar için tekil kimlik: campaignId, yoksa eventToken, yoksa raw. */
    internal val notificationId: Int
        get() = (campaignId ?: eventToken ?: raw).hashCode()
}

/**
 * Bildirime (ya da bir aksiyon düğmesine) dokunulduğunda ana iş parçacığında çağrılır.
 * `true` dönerse SDK varsayılan açmayı yapmaz — yönlendirmeyi siz yaptınız.
 */
public fun interface NotificationOpenedHandler {
    public fun onOpened(notification: BildirimNotification): Boolean
}

/**
 * Uygulama ön plandayken bildirim geldiğinde çağrılır. `true` → SDK bildirimi çizer,
 * `false` → çizmez (bu durumda "gösterildi" ölçümü de yapılmaz).
 */
public fun interface ForegroundHandler {
    public fun onForeground(notification: BildirimNotification): Boolean
}

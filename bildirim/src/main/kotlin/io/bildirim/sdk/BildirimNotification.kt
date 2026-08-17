package io.bildirim.sdk

/** Bildirimdeki bir aksiyon düğmesi (`a[]`). `url` yoksa düğme bildirimin kendi adresini açar. */
public data class BildirimAction(
    val id: String,
    val label: String,
    val url: String?,
)

/**
 * Sunucudan gelen `bildirim` sözlüğünün çözümlenmiş hâli. Handler'lara bu verilir.
 * Alan adları sözleşmedeki kısa anahtarların açılımıdır (c→campaignId, t→eventToken, …).
 */
public data class BildirimNotification(
    val campaignId: String?,
    val eventToken: String?,
    val title: String?,
    val body: String?,
    val url: String?,
    val imageUrl: String?,
    val iconUrl: String?,
    val actions: List<BildirimAction>,
    /** Ham `bildirim` JSON dizgesi — ileride eklenecek alanlar için. */
    val raw: String,
) {
    /** Bildirim ve aksiyonlar için tekil kimlik: campaignId, yoksa eventToken, yoksa raw. */
    internal val notificationId: Int
        get() = (campaignId ?: eventToken ?: raw).hashCode()
}

/** Kullanıcı bildirime (ya da bir aksiyon düğmesine) dokunduğunda handler'a verilen bilgi. */
public data class BildirimOpenResult(
    val notification: BildirimNotification,
    /** Dokunulan aksiyon düğmesinin kimliği; gövdeye dokunulduysa null. */
    val actionId: String?,
    /** Açılması beklenen adres: aksiyonun `url`'i, yoksa bildirimin `url`'i, yoksa null. */
    val url: String?,
)

/**
 * Bildirim tıklandığında çağrılır. `true` dönerse uygulama yönlendirmeyi kendisi yapmıştır,
 * SDK adresi açmaz. `false` dönerse SDK `url`'i açar (deep link → uygulama, https → tarayıcı,
 * yoksa uygulamanın ana ekranı).
 */
public fun interface NotificationOpenedHandler {
    public fun onOpened(result: BildirimOpenResult): Boolean
}

/**
 * Uygulama ön plandayken bir bildirim geldiğinde çağrılır. `true` → SDK bildirimi çizer,
 * `false` → çizmez (uygulama içeriği kendi arayüzünde gösterebilir; bu durumda "gösterildi"
 * ölçümü de yapılmaz).
 */
public fun interface ForegroundHandler {
    public fun onForeground(notification: BildirimNotification): Boolean
}

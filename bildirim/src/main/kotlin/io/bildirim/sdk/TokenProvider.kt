package io.bildirim.sdk

/**
 * Cihaz jetonunu getirir. Üretimde Firebase (varsayılan); yalnız test/özel kurulumlar için
 * [BildirimConfig.tokenProvider] ile değiştirilir.
 */
public fun interface TokenProvider {
    public fun fetch(callback: (String?) -> Unit)
}

package io.bildirim.sdk

/**
 * SDK sürümü — her `/v1/subscribe` isteğinde `sdkVersion` olarak gider.
 * gradle.properties → VERSION_NAME ile aynı olmalı (scripts/bump-version.sh ikisini birlikte günceller,
 * ContractTest de eşitliği doğrular).
 */
public object BildirimVersion {
    public const val SDK_VERSION: String = "1.0.1"
}

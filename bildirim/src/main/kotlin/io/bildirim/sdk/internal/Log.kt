package io.bildirim.sdk.internal

import io.bildirim.sdk.BildirimConfig

internal object Log {
    const val TAG = "Bildirim"
    @Volatile var level: Int = BildirimConfig.LOG_INFO

    fun d(msg: String) { if (level >= BildirimConfig.LOG_DEBUG) android.util.Log.d(TAG, msg) }
    fun i(msg: String) { if (level >= BildirimConfig.LOG_INFO) android.util.Log.i(TAG, msg) }
    fun w(msg: String, t: Throwable? = null) { if (level >= BildirimConfig.LOG_ERROR) android.util.Log.w(TAG, msg, t) }
    fun e(msg: String, t: Throwable? = null) { if (level >= BildirimConfig.LOG_ERROR) android.util.Log.e(TAG, msg, t) }
}

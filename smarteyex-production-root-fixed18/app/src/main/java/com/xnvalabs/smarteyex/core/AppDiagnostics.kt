package com.xnvalabs.smarteyex.core

import android.util.Log

/** Central logging boundary. Never log raw user messages, images, tokens or memory content. */
object AppDiagnostics {
    private const val TAG = "SmartEyeX"

    fun info(event: String) = Log.i(TAG, sanitize(event))
    fun warn(event: String, error: Throwable? = null) = Log.w(TAG, sanitize(event), error)
    fun error(event: String, error: Throwable? = null) = Log.e(TAG, sanitize(event), error)

    private fun sanitize(event: String): String =
        event.replace(Regex("[\\r\\n\\t]"), " ").take(500)
}

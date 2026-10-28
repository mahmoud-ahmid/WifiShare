package com.example.wifishare.core

import android.util.Log
import com.example.wifishare.BuildConfig

/**
 * غلاف بسيط فوق Log لا يعمل إلا في Debug.
 * يضمن عدم طباعة أي بيانات حساسة في الإصدار النهائي.
 */
object Logger {
    private const val TAG = "WifiShare"
    private val enabled: Boolean = BuildConfig.DEBUG

    fun d(message: String) { if (enabled) Log.d(TAG, message) }
    fun i(message: String) { if (enabled) Log.i(TAG, message) }
    fun w(message: String, t: Throwable? = null) { if (enabled) Log.w(TAG, message, t) }
    fun e(message: String, t: Throwable? = null) { if (enabled) Log.e(TAG, message, t) }
}

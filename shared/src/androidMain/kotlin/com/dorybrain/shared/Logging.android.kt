package com.dorybrain.shared

import android.util.Log

actual fun logWarning(tag: String, message: String, throwable: Throwable?) {
    Log.w(tag, message, throwable)
}

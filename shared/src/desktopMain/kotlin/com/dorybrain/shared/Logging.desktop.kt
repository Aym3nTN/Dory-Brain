package com.dorybrain.shared

actual fun logWarning(tag: String, message: String, throwable: Throwable?) {
    System.err.println("W/$tag: $message")
    throwable?.printStackTrace()
}

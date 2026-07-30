package com.dorybrain.shared

/** Minimal logging seam so shared code isn't tied to android.util.Log. */
expect fun logWarning(tag: String, message: String, throwable: Throwable? = null)

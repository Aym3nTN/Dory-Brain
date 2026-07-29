package com.dorybrain.app.ui.speech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Drives on-device dictation for the capture field.
 *
 * [partialText] streams interim words while the user is talking so the field
 * fills in live; the final transcript is handed to the `onFinalText` callback
 * given to [rememberSpeechInput].
 */
class SpeechInputController internal constructor(
    private val context: Context,
    private val onFinalText: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onPartialText: (String) -> Unit,
    private val requestPermission: () -> Unit
) {
    var isListening by mutableStateOf(false)
        private set

    var partialText by mutableStateOf("")
        private set

    private var recognizer: SpeechRecognizer? = null

    val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    private val hasPermission: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun toggle() {
        if (isListening) stop() else start()
    }

    fun start() {
        if (isListening) return

        if (!isAvailable) {
            onError("No speech recognition available on this device.")
            return
        }
        if (!hasPermission) {
            requestPermission()
            return
        }

        val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).also {
            recognizer = it
        }
        speechRecognizer.setRecognitionListener(listener)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        partialText = ""
        isListening = true
        runCatching { speechRecognizer.startListening(intent) }
            .onFailure {
                isListening = false
                release()
                onError("Couldn't start dictation.")
            }
    }

    fun stop() {
        // Ask for a final result rather than cancelling, so whatever was
        // already spoken still makes it into the note.
        runCatching { recognizer?.stopListening() }
        isListening = false
    }

    internal fun onPermissionResult(granted: Boolean) {
        if (granted) start() else onError("Microphone permission is needed to dictate notes.")
    }

    internal fun release() {
        runCatching { recognizer?.destroy() }
        recognizer = null
        isListening = false
        partialText = ""
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() {
            isListening = false
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            firstResult(partialResults)?.let {
                partialText = it
                onPartialText(it)
            }
        }

        override fun onResults(results: Bundle?) {
            val text = firstResult(results)
            isListening = false
            partialText = ""
            release()
            if (text.isNullOrBlank()) {
                onError("Didn't catch that — try again.")
            } else {
                onFinalText(text)
            }
        }

        override fun onError(error: Int) {
            val wasListening = isListening
            isListening = false
            val salvaged = partialText
            partialText = ""
            release()

            // A no-match/timeout after we already have words isn't worth an
            // error toast — keep what was heard.
            if (salvaged.isNotBlank() &&
                (error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
            ) {
                onFinalText(salvaged)
                return
            }

            // Suppress the spurious error the framework emits when the user
            // themselves stopped listening.
            if (!wasListening && error == SpeechRecognizer.ERROR_CLIENT) return

            onError(describe(error))
        }

        private fun firstResult(bundle: Bundle?): String? =
            bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.takeIf { it.isNotBlank() }

        private fun describe(error: Int): String = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Microphone trouble — try again."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                "Microphone permission is needed to dictate notes."
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "Speech recognition needs a network connection right now."
            SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that — try again."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer is busy — try again."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't hear anything."
            else -> "Dictation failed — try again."
        }
    }
}

/**
 * Remembers a [SpeechInputController] bound to the current composition,
 * wiring up the RECORD_AUDIO permission prompt and tearing the recognizer
 * down when the caller leaves the screen.
 */
@Composable
fun rememberSpeechInput(
    onFinalText: (String) -> Unit,
    onPartialText: (String) -> Unit = {},
    onError: (String) -> Unit = {}
): SpeechInputController {
    val context = LocalContext.current
    val currentFinal by rememberUpdatedState(onFinalText)
    val currentPartial by rememberUpdatedState(onPartialText)
    val currentError by rememberUpdatedState(onError)

    // Holder lets the controller and its permission launcher reference each
    // other despite being created in sequence.
    val holder = remember { arrayOfNulls<SpeechInputController>(1) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        holder[0]?.onPermissionResult(granted)
    }

    val controller = remember {
        SpeechInputController(
            context = context,
            onFinalText = { currentFinal(it) },
            onError = { currentError(it) },
            onPartialText = { currentPartial(it) },
            requestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
        ).also { holder[0] = it }
    }

    DisposableEffect(controller) {
        onDispose { controller.release() }
    }

    return controller
}

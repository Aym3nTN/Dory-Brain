package com.dorybrain.app.ui.speech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Continuous dictation for the capture field.
 *
 * Android's [SpeechRecognizer] is built for one short utterance: it decides on
 * its own that you've stopped talking and finishes the session. The
 * `EXTRA_SPEECH_INPUT_*_SILENCE_LENGTH_MILLIS` extras below ask for a longer
 * tolerance, but they're documented as hints and most recognizers (Google's
 * included) ignore them.
 *
 * So a dictation *session* here is not one recognizer session. The mic stays
 * open until the user stops it: whenever the recognizer finishes a segment —
 * whether with a result, a no-match, or a speech timeout from a long pause —
 * it is immediately started again, and each finished segment is appended to a
 * running transcript. From the user's side that reads as "the mic stayed on
 * through my pause".
 *
 * [transcript] is always the whole session so far (finished segments plus the
 * in-flight partial), so callers can render it directly rather than stitching
 * segments themselves.
 */
class SpeechInputController internal constructor(
    private val context: Context,
    private val onTranscript: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val requestPermission: () -> Unit
) {
    /** True for as long as the user wants to be dictating, pauses included. */
    var isListening by mutableStateOf(false)
        private set

    /** True only while the recognizer currently hears speech. */
    var isHearingSpeech by mutableStateOf(false)
        private set

    var transcript by mutableStateOf("")
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private val session = DictationSession()

    /** User intent, as opposed to whether a recognizer segment is running. */
    private var sessionActive = false

    private var restartScheduled = false
    private var segmentStartedAt = 0L

    val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    private val hasPermission: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun toggle() {
        if (sessionActive) stop() else start()
    }

    fun start() {
        if (sessionActive) return

        if (!isAvailable) {
            onError("No speech recognition available on this device.")
            return
        }
        if (!hasPermission) {
            requestPermission()
            return
        }

        sessionActive = true
        isListening = true
        session.reset()
        transcript = ""

        beginSegment()
    }

    /** Ends the session. Whatever was already heard is kept. */
    fun stop() {
        if (!sessionActive) return

        sessionActive = false
        isListening = false
        isHearingSpeech = false
        handler.removeCallbacksAndMessages(null)
        restartScheduled = false

        // Ask for a final result rather than cancelling, so the last words
        // still land, then hard-stop shortly after in case nothing arrives.
        runCatching { recognizer?.stopListening() }
        handler.postDelayed(::finishSession, FINAL_RESULT_GRACE_MS)
    }

    internal fun onPermissionResult(granted: Boolean) {
        if (granted) start() else onError("Microphone permission is needed to dictate notes.")
    }

    internal fun release() {
        sessionActive = false
        handler.removeCallbacksAndMessages(null)
        restartScheduled = false
        runCatching { recognizer?.destroy() }
        recognizer = null
        isListening = false
        isHearingSpeech = false
    }

    // ---- session plumbing ----

    private fun beginSegment() {
        if (!sessionActive) return

        val speechRecognizer = recognizer ?: SpeechRecognizer
            .createSpeechRecognizer(context)
            .also {
                it.setRecognitionListener(listener)
                recognizer = it
            }

        segmentStartedAt = SystemClock.elapsedRealtime()
        runCatching { speechRecognizer.startListening(buildIntent()) }
            .onFailure {
                onError("Couldn't start dictation.")
                release()
            }
    }

    private fun buildIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)

        // Best-effort: ask the recognizer to tolerate long pauses itself.
        // Widely ignored, which is why the restart loop above exists — treat
        // these as a bonus when honoured, never as the mechanism.
        putExtra(
            RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
            SILENCE_TOLERANCE_MS
        )
        putExtra(
            RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
            SILENCE_TOLERANCE_MS
        )
        putExtra(
            RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
            MINIMUM_SESSION_MS
        )
    }

    /** Starts the next segment so the mic appears to stay open. */
    private fun scheduleRestart(delayMs: Long = RESTART_DELAY_MS) {
        if (!sessionActive || restartScheduled) return

        restartScheduled = true
        handler.postDelayed({
            restartScheduled = false
            if (sessionActive) beginSegment()
        }, delayMs)
    }

    private fun finishSession() {
        session.commitPartial()
        emitTranscript()
        release()
    }

    private fun emitTranscript() {
        transcript = session.transcript
        onTranscript(transcript)
    }

    /** Carries out whatever the session decided should happen next. */
    private fun apply(next: DictationSession.Next) {
        emitTranscript()
        when (next) {
            is DictationSession.Next.Restart -> scheduleRestart(next.delayMs)
            is DictationSession.Next.Fail -> {
                onError(next.message)
                release()
            }
            DictationSession.Next.Finish -> {
                handler.removeCallbacksAndMessages(null)
                release()
            }
        }
    }

    /**
     * A segment that fails almost immediately means something is actually
     * wrong (recognizer unavailable, audio route broken) rather than the user
     * pausing — restarting on those would spin a tight loop on the mic.
     */
    private fun wasRapidFailure(): Boolean =
        SystemClock.elapsedRealtime() - segmentStartedAt < RAPID_FAILURE_WINDOW_MS

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit

        override fun onBeginningOfSpeech() {
            isHearingSpeech = true
        }

        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            // Only this segment ended. The session keeps going.
            isHearingSpeech = false
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            firstResult(partialResults)?.let {
                session.onPartial(it)
                emitTranscript()
            }
        }

        override fun onResults(results: Bundle?) {
            isHearingSpeech = false
            apply(session.onSegmentResult(firstResult(results), sessionActive))
        }

        override fun onError(error: Int) {
            isHearingSpeech = false
            apply(
                session.onSegmentError(
                    errorCode = error,
                    wasRapid = wasRapidFailure(),
                    sessionActive = sessionActive
                )
            )
        }

        private fun firstResult(bundle: Bundle?): String? =
            bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.takeIf { it.isNotBlank() }
    }

    private companion object {
        /** Hint only; see [buildIntent]. */
        const val SILENCE_TOLERANCE_MS = 10_000
        const val MINIMUM_SESSION_MS = 60_000

        const val RESTART_DELAY_MS = DictationSession.RESTART_DELAY_MS
        const val FINAL_RESULT_GRACE_MS = 1_200L

        const val RAPID_FAILURE_WINDOW_MS = DictationSession.RAPID_FAILURE_WINDOW_MS
    }
}

/**
 * Remembers a [SpeechInputController] bound to the current composition,
 * wiring up the RECORD_AUDIO permission prompt and tearing the recognizer
 * down when the caller leaves the screen.
 *
 * [onTranscript] receives the whole session transcript each time it changes,
 * not just the newest words.
 */
@Composable
fun rememberSpeechInput(
    onTranscript: (String) -> Unit,
    onError: (String) -> Unit = {}
): SpeechInputController {
    val context = LocalContext.current
    val currentTranscript by rememberUpdatedState(onTranscript)
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
            onTranscript = { currentTranscript(it) },
            onError = { currentError(it) },
            requestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
        ).also { holder[0] = it }
    }

    DisposableEffect(controller) {
        onDispose { controller.release() }
    }

    return controller
}

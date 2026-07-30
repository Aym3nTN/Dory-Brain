package com.dorybrain.app.ui.speech

import android.speech.SpeechRecognizer

/**
 * The decision-making half of continuous dictation, kept free of Android
 * framework objects so it can be unit tested.
 *
 * [SpeechInputController] owns the recognizer and the Handler; this owns the
 * running transcript and the question "given what the recognizer just did,
 * should we listen again, stop, or report a problem?".
 */
internal class DictationSession {

    /** Segments the recognizer has finalized, joined with spaces. */
    var committed: String = ""
        private set

    /** The in-flight segment, not yet finalized. */
    var partial: String = ""
        private set

    /**
     * Consecutive failures that arrived too fast to be a human pause. Used to
     * stop a broken recognizer from spinning in a restart loop.
     */
    var rapidFailures: Int = 0
        private set

    /**
     * Whatever was in the field before dictation started. Held here rather
     * than in the UI so the text handed back is always the complete field
     * contents, and can't be rebuilt from a stale captured value.
     */
    var baseline: String = ""
        private set

    /** Everything heard this session: finalized segments plus the partial. */
    val spoken: String
        get() = buildString {
            append(committed)
            if (partial.isNotBlank()) {
                if (isNotEmpty()) append(' ')
                append(partial)
            }
        }

    /** [baseline] plus everything heard — what the capture field should show. */
    val transcript: String
        get() {
            val heard = spoken
            return when {
                baseline.isBlank() -> heard
                heard.isBlank() -> baseline
                else -> "${baseline.trimEnd()} $heard"
            }
        }

    /** What the controller should do next. */
    sealed interface Next {
        /** Start another recognizer segment so the mic appears to stay open. */
        data class Restart(val delayMs: Long = RESTART_DELAY_MS) : Next

        /** Stop, and tell the user why. */
        data class Fail(val message: String) : Next

        /** Stop quietly; the session is already over. */
        data object Finish : Next
    }

    fun reset(baseline: String = "") {
        this.baseline = baseline
        committed = ""
        partial = ""
        rapidFailures = 0
    }

    fun onPartial(text: String) {
        if (text.isBlank()) return
        partial = text
        rapidFailures = 0
    }

    /**
     * Folds [text] into [committed].
     *
     * Recognizers differ in what a restarted segment returns: most give just
     * the new words, but some redeliver the whole utterance so far, and some
     * repeat the previous segment verbatim. Appending blindly would duplicate
     * in the first case; replacing blindly would erase in the second. So the
     * three cases are told apart explicitly, and [committed] is only ever
     * allowed to grow — a session must never lose words already heard.
     */
    fun commit(text: String?) {
        val segment = text?.trim().orEmpty()
        partial = ""
        if (segment.isEmpty()) return

        val existing = committed
        committed = when {
            existing.isEmpty() -> segment

            // Cumulative recognizer: this segment restates everything so far.
            segment.length > existing.length &&
                segment.startsWith(existing, ignoreCase = true) -> segment

            // Redelivery of something already recorded — nothing new to add.
            existing.endsWith(segment, ignoreCase = true) -> existing

            else -> "$existing $segment"
        }
        rapidFailures = 0
    }

    /** Commits whatever partial text exists, e.g. before a restart or stop. */
    fun commitPartial() {
        if (partial.isNotBlank()) commit(partial)
    }

    fun onSegmentResult(text: String?, sessionActive: Boolean): Next {
        // Fall back to the partial when the recognizer returns nothing useful.
        commit(text?.takeIf { it.isNotBlank() } ?: partial)
        return if (sessionActive) Next.Restart() else Next.Finish
    }

    /**
     * @param wasRapid whether the segment failed too quickly to be a pause.
     * @param sessionActive whether the user still wants to be dictating.
     */
    fun onSegmentError(errorCode: Int, wasRapid: Boolean, sessionActive: Boolean): Next {
        if (!sessionActive) {
            // ERROR_CLIENT is the spurious one the framework emits when we
            // ourselves stopped it — there's no partial worth keeping then.
            if (errorCode != SpeechRecognizer.ERROR_CLIENT) commitPartial()
            return Next.Finish
        }

        if (isPauseLike(errorCode)) {
            // The user pausing surfaces as exactly these. Normal, not failure.
            commitPartial()
            rapidFailures = if (wasRapid) rapidFailures + 1 else 0

            return if (rapidFailures >= MAX_RAPID_FAILURES) {
                Next.Fail("Dictation kept dropping out — stopped listening.")
            } else {
                Next.Restart()
            }
        }

        if (errorCode == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
            // Keep anything heard before restarting, same as the pause path.
            commitPartial()
            rapidFailures += 1
            return if (rapidFailures >= MAX_RAPID_FAILURES) {
                Next.Fail("Recognizer is busy — stopped listening.")
            } else {
                Next.Restart(BUSY_RETRY_DELAY_MS)
            }
        }

        commitPartial()
        return Next.Fail(describe(errorCode))
    }

    private fun isPauseLike(errorCode: Int) =
        errorCode == SpeechRecognizer.ERROR_NO_MATCH ||
            errorCode == SpeechRecognizer.ERROR_SPEECH_TIMEOUT

    private fun describe(errorCode: Int): String = when (errorCode) {
        SpeechRecognizer.ERROR_AUDIO -> "Microphone trouble — try again."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            "Microphone permission is needed to dictate notes."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Speech recognition needs a network connection right now."
        SpeechRecognizer.ERROR_SERVER -> "The speech service had a problem — try again."
        SpeechRecognizer.ERROR_CLIENT -> "Dictation stopped unexpectedly."
        else -> "Dictation failed — try again."
    }

    companion object {
        const val RESTART_DELAY_MS = 120L
        const val BUSY_RETRY_DELAY_MS = 500L

        /** How quickly a failure must arrive to count as "not a pause". */
        const val RAPID_FAILURE_WINDOW_MS = 400L
        const val MAX_RAPID_FAILURES = 5
    }
}

/**
 * Maps [RecognitionListener.onRmsChanged]'s value onto 0f..1f for the
 * waveform. The callback's range isn't specified precisely; in practice it
 * sits around -2dB for silence up to about 10dB for loud speech, and some
 * devices report outside that, so the result is clamped.
 */
internal fun normalizeRms(
    rmsdB: Float,
    floorDb: Float = RMS_FLOOR_DB,
    ceilingDb: Float = RMS_CEILING_DB
): Float = ((rmsdB - floorDb) / (ceilingDb - floorDb)).coerceIn(0f, 1f)

internal const val RMS_FLOOR_DB = -2f
internal const val RMS_CEILING_DB = 10f

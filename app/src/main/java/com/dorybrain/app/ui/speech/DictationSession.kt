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

    /** Everything heard this session: finalized segments plus the partial. */
    val transcript: String
        get() = buildString {
            append(committed)
            if (partial.isNotBlank()) {
                if (isNotEmpty()) append(' ')
                append(partial)
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

    fun reset() {
        committed = ""
        partial = ""
        rapidFailures = 0
    }

    fun onPartial(text: String) {
        if (text.isBlank()) return
        partial = text
        rapidFailures = 0
    }

    /** Folds [text] into [committed]; blank text is ignored. */
    fun commit(text: String?) {
        val segment = text?.trim().orEmpty()
        partial = ""
        if (segment.isEmpty()) return

        committed = if (committed.isEmpty()) segment else "$committed $segment"
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

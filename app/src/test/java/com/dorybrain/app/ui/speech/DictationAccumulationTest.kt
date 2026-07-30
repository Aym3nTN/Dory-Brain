package com.dorybrain.app.ui.speech

import android.speech.SpeechRecognizer
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression cover for text being erased across a pause.
 *
 * Recognizers disagree about what a restarted segment returns — some give
 * only the new words, some restate the whole utterance, some redeliver the
 * previous segment. Each of those is simulated here, because the reported
 * symptom ("the dictation starts over and erases what I said") is what the
 * cumulative case looks like if the result is appended, and what the
 * redelivery case looks like if the result replaces.
 */
class DictationAccumulationTest {

    private val session = DictationSession()

    private fun pause() = session.onSegmentError(
        errorCode = SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
        wasRapid = false,
        sessionActive = true
    )

    // ---- the reported bug ----

    @Test
    fun `text typed before dictation is kept and added to, not replaced`() {
        session.reset(baseline = "Shopping list:")

        session.onPartial("buy almond milk")
        pause()
        session.onPartial("and eggs")

        assertEquals("Shopping list: buy almond milk and eggs", session.transcript)
    }

    @Test
    fun `a recognizer that only returns new words appends them`() {
        session.reset()

        session.onSegmentResult("buy almond milk", sessionActive = true)
        session.onSegmentResult("and eggs", sessionActive = true)

        assertEquals("buy almond milk and eggs", session.transcript)
    }

    @Test
    fun `a recognizer that restates everything does not duplicate`() {
        session.reset()

        session.onSegmentResult("buy almond milk", sessionActive = true)
        // Cumulative: the second segment repeats the first plus the new words.
        session.onSegmentResult("buy almond milk and eggs", sessionActive = true)

        assertEquals("buy almond milk and eggs", session.transcript)
    }

    @Test
    fun `a recognizer that redelivers the same segment adds nothing`() {
        session.reset()

        session.onSegmentResult("buy almond milk", sessionActive = true)
        session.onSegmentResult("buy almond milk", sessionActive = true)

        assertEquals("buy almond milk", session.transcript)
    }

    @Test
    fun `committed text never shrinks across a long dictation`() {
        session.reset(baseline = "Note:")
        var longest = 0

        val segments = listOf(
            "first sentence",
            "first sentence second sentence", // cumulative device
            "third sentence",                 // incremental again
            "third sentence",                 // redelivery
            "fourth sentence"
        )

        segments.forEach { segment ->
            session.onPartial(segment)
            pause()
            val length = session.transcript.length
            assert(length >= longest) {
                "transcript shrank to \"${session.transcript}\" after \"$segment\""
            }
            longest = length
        }

        assertEquals(
            "Note: first sentence second sentence third sentence fourth sentence",
            session.transcript
        )
    }

    @Test
    fun `several pauses with an empty baseline still accumulate`() {
        session.reset()

        listOf("one", "two", "three", "four").forEach {
            session.onPartial(it)
            pause()
        }

        assertEquals("one two three four", session.transcript)
    }

    // ---- baseline handling ----

    @Test
    fun `baseline alone shows when nothing has been said yet`() {
        session.reset(baseline = "already typed")

        assertEquals("already typed", session.transcript)
    }

    @Test
    fun `a blank baseline adds no leading space`() {
        session.reset(baseline = "")
        session.onPartial("spoken words")

        assertEquals("spoken words", session.transcript)
    }

    @Test
    fun `trailing whitespace in the baseline is not doubled`() {
        session.reset(baseline = "typed   ")
        session.onPartial("spoken")

        assertEquals("typed spoken", session.transcript)
    }

    @Test
    fun `resetting for a new session drops the previous baseline`() {
        session.reset(baseline = "first session")
        session.onPartial("some words")

        session.reset(baseline = "second session")

        assertEquals("second session", session.transcript)
    }

    @Test
    fun `a busy recognizer restart keeps what was already heard`() {
        session.reset()
        session.onPartial("do not lose me")

        session.onSegmentError(
            errorCode = SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            wasRapid = true,
            sessionActive = true
        )

        assertEquals("do not lose me", session.transcript)
    }
}

package com.dorybrain.app.ui.speech

import android.speech.SpeechRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the behaviour that makes dictation feel continuous: a pause must
 * restart the recognizer and keep the words already heard, and only a genuine
 * fault may end the session.
 *
 * These run on the JVM without a device — the `SpeechRecognizer.ERROR_*`
 * values are compile-time constants, so nothing Android-specific is loaded.
 */
class DictationSessionTest {

    private val session = DictationSession()

    private fun pause(wasRapid: Boolean = false) = session.onSegmentError(
        errorCode = SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
        wasRapid = wasRapid,
        sessionActive = true
    )

    // ---- the long-pause case ----

    @Test
    fun `a speech timeout mid-session restarts instead of ending`() {
        session.onPartial("remind me to")

        val next = pause()

        assertTrue("a pause must restart the mic, got $next", next is DictationSession.Next.Restart)
    }

    @Test
    fun `a no-match mid-session also restarts`() {
        val next = session.onSegmentError(
            errorCode = SpeechRecognizer.ERROR_NO_MATCH,
            wasRapid = false,
            sessionActive = true
        )

        assertTrue(next is DictationSession.Next.Restart)
    }

    @Test
    fun `words heard before a pause survive it`() {
        session.onPartial("buy almond milk")

        pause()

        assertEquals("buy almond milk", session.transcript)
    }

    @Test
    fun `speech after a pause is appended, not replaced`() {
        session.onPartial("call the dentist")
        pause()

        session.onPartial("and book a cleaning")
        session.onSegmentResult("and book a cleaning", sessionActive = true)

        assertEquals("call the dentist and book a cleaning", session.transcript)
    }

    @Test
    fun `many consecutive pauses keep accumulating`() {
        listOf("first thought", "second thought", "third thought").forEach { segment ->
            session.onPartial(segment)
            pause()
        }

        assertEquals("first thought second thought third thought", session.transcript)
    }

    @Test
    fun `a pause with nothing said yet still restarts and stays empty`() {
        val next = pause()

        assertTrue(next is DictationSession.Next.Restart)
        assertEquals("", session.transcript)
    }

    @Test
    fun `slow pauses never trip the failure guard however many there are`() {
        repeat(DictationSession.MAX_RAPID_FAILURES * 3) {
            val next = pause(wasRapid = false)
            assertTrue("pause $it should restart, got $next", next is DictationSession.Next.Restart)
        }
        assertEquals(0, session.rapidFailures)
    }

    // ---- guarding against a broken recognizer ----

    @Test
    fun `failures arriving too fast to be pauses eventually stop the session`() {
        repeat(DictationSession.MAX_RAPID_FAILURES - 1) {
            assertTrue(pause(wasRapid = true) is DictationSession.Next.Restart)
        }

        val next = pause(wasRapid = true)
        assertTrue("should give up after repeated instant failures", next is DictationSession.Next.Fail)
    }

    @Test
    fun `hearing something resets the rapid failure count`() {
        repeat(DictationSession.MAX_RAPID_FAILURES - 1) { pause(wasRapid = true) }
        assertTrue(session.rapidFailures > 0)

        session.onPartial("actually I can hear you now")

        assertEquals(0, session.rapidFailures)
        assertTrue(pause(wasRapid = true) is DictationSession.Next.Restart)
    }

    @Test
    fun `a busy recognizer is retried with a longer delay`() {
        val next = session.onSegmentError(
            errorCode = SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            wasRapid = true,
            sessionActive = true
        )

        assertEquals(
            DictationSession.Next.Restart(DictationSession.BUSY_RETRY_DELAY_MS),
            next
        )
    }

    // ---- real faults still end the session ----

    @Test
    fun `a permissions error ends the session with a message`() {
        val next = session.onSegmentError(
            errorCode = SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
            wasRapid = false,
            sessionActive = true
        )

        assertTrue(next is DictationSession.Next.Fail)
        assertTrue((next as DictationSession.Next.Fail).message.contains("permission"))
    }

    @Test
    fun `a network error ends the session but keeps what was heard`() {
        session.onPartial("half a thought")

        val next = session.onSegmentError(
            errorCode = SpeechRecognizer.ERROR_NETWORK,
            wasRapid = false,
            sessionActive = true
        )

        assertTrue(next is DictationSession.Next.Fail)
        assertEquals("half a thought", session.transcript)
    }

    // ---- stopping ----

    @Test
    fun `a result after the user stops finishes rather than restarting`() {
        session.onPartial("last words")

        val next = session.onSegmentResult("last words", sessionActive = false)

        assertEquals(DictationSession.Next.Finish, next)
        assertEquals("last words", session.transcript)
    }

    @Test
    fun `stopping keeps the in-flight partial`() {
        session.onPartial("almost finished")

        session.commitPartial()

        assertEquals("almost finished", session.transcript)
    }

    @Test
    fun `the spurious client error on user stop is not reported as a failure`() {
        val next = session.onSegmentError(
            errorCode = SpeechRecognizer.ERROR_CLIENT,
            wasRapid = true,
            sessionActive = false
        )

        assertEquals(DictationSession.Next.Finish, next)
    }

    // ---- transcript assembly ----

    @Test
    fun `transcript shows committed text plus the live partial`() {
        session.commit("first part")
        session.onPartial("being spoken now")

        assertEquals("first part being spoken now", session.transcript)
    }

    @Test
    fun `an empty result falls back to the partial rather than losing it`() {
        session.onPartial("only in the partial")

        session.onSegmentResult(null, sessionActive = true)

        assertEquals("only in the partial", session.transcript)
    }

    @Test
    fun `blank segments never introduce stray whitespace`() {
        session.commit("  spaced out  ")
        session.commit("")
        session.commit("   ")

        assertEquals("spaced out", session.transcript)
    }

    @Test
    fun `reset clears everything for a fresh session`() {
        session.commit("old session")
        session.onPartial("still talking")

        session.reset()

        assertEquals("", session.transcript)
        assertEquals(0, session.rapidFailures)
    }
}

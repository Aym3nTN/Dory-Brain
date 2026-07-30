package com.dorybrain.app.ui.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The waveform is only meaningful if quiet and loud map to sane values. */
class NormalizeRmsTest {

    @Test
    fun `silence maps to the bottom of the range`() {
        assertEquals(0f, normalizeRms(RMS_FLOOR_DB), 0.001f)
    }

    @Test
    fun `loud speech maps to the top of the range`() {
        assertEquals(1f, normalizeRms(RMS_CEILING_DB), 0.001f)
    }

    @Test
    fun `mid-level speech lands mid-range`() {
        val middle = (RMS_FLOOR_DB + RMS_CEILING_DB) / 2f
        assertEquals(0.5f, normalizeRms(middle), 0.001f)
    }

    @Test
    fun `devices reporting below the floor are clamped, not negative`() {
        // A negative bar height would draw upside down.
        assertEquals(0f, normalizeRms(-120f), 0.001f)
    }

    @Test
    fun `devices reporting above the ceiling are clamped to one`() {
        assertEquals(1f, normalizeRms(500f), 0.001f)
    }

    @Test
    fun `louder input never produces a smaller level`() {
        var previous = -1f
        var db = RMS_FLOOR_DB - 5f
        while (db <= RMS_CEILING_DB + 5f) {
            val level = normalizeRms(db)
            assertTrue("level dropped at ${db}dB", level >= previous)
            previous = level
            db += 0.5f
        }
    }
}

package app.last10min.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.ZonedDateTime

class EventEncoderTest {
    private val encoder = EventEncoder("install-secret".toByteArray())

    private fun endingAt(iso: String) = DetectedEvent(
        EventKind.PARK_COMPLETE, "lot-a", "parking",
        startMs = ZonedDateTime.parse(iso).toInstant().toEpochMilli() - 125_000,
        endMs = ZonedDateTime.parse(iso).toInstant().toEpochMilli(),
    )

    @Test
    fun `time is reduced to Seoul weekday, 15 minute bucket and local date`() {
        val w = encoder.encode(endingAt("2026-11-12T12:52:30Z"))

        assertEquals(4, w.weekday)
        assertEquals((21 * 60 + 45) / 15, w.timeBucket)
        assertEquals("2026-11-12", w.day)
        assertEquals(125, w.durationSec)
        assertEquals("park_complete", w.event)
    }

    @Test
    fun `daily token is stable within a day and unlinkable across days`() {
        val morning = encoder.encode(endingAt("2026-11-12T00:10:00Z")).dailyToken
        val evening = encoder.encode(endingAt("2026-11-12T14:00:00Z")).dailyToken
        val nextDay = encoder.encode(endingAt("2026-11-12T15:30:00Z")).dailyToken

        assertEquals(morning, evening)
        assertNotEquals(evening, nextDay)
        assertEquals(32, morning.length)
    }
}

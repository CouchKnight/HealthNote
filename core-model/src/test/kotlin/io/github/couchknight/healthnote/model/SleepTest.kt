package io.github.couchknight.healthnote.model

import io.github.couchknight.healthnote.model.sample.SampleData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

class SleepTest {
    private val london = ZoneId.of("Europe/London")

    private fun at(s: String) = Instant.parse(s)

    @Test
    fun `a session crossing midnight belongs to the wake date`() {
        val s = SleepSession(at("2026-09-21T22:24:00Z"), at("2026-09-22T05:36:00Z"))
        val nights = SleepNightAttributor(london).attribute(listOf(s))
        val n = nights.getValue(LocalDate.of(2026, 9, 22))
        assertEquals(LocalTime.of(23, 24), n.bedtime.toLocalTime())
        assertEquals(LocalTime.of(6, 36), n.wake.toLocalTime())
        assertEquals(Duration.ofMinutes(432), n.total)
    }

    @Test
    fun `bed date attribution is available`() {
        val s = SleepSession(at("2026-09-21T22:24:00Z"), at("2026-09-22T05:36:00Z"))
        val nights = SleepNightAttributor(london, NightAttribution.BED_DATE).attribute(listOf(s))
        assertEquals(setOf(LocalDate.of(2026, 9, 21)), nights.keys)
    }

    @Test
    fun `a night across the autumn clock change reports the time actually slept`() {
        // 23:00 BST on 24 Oct to 07:00 GMT on 25 Oct: the wall clock says 8h, the night was 9h.
        val s = SleepSession(at("2026-10-24T22:00:00Z"), at("2026-10-25T07:00:00Z"))
        val n = SleepNightAttributor(london).attribute(listOf(s)).getValue(LocalDate.of(2026, 10, 25))
        assertEquals(Duration.ofHours(9), n.total)
        assertEquals(LocalTime.of(23, 0), n.bedtime.toLocalTime())
        assertEquals(LocalTime.of(7, 0), n.wake.toLocalTime())
    }

    @Test
    fun `several sessions on one night merge and stages sum`() {
        val a = SleepSession(
            at("2026-09-21T22:00:00Z"), at("2026-09-22T01:00:00Z"),
            listOf(
                SleepStage(at("2026-09-21T22:00:00Z"), at("2026-09-21T23:00:00Z"), SleepStageType.LIGHT),
                SleepStage(at("2026-09-21T23:00:00Z"), at("2026-09-22T00:00:00Z"), SleepStageType.DEEP),
                SleepStage(at("2026-09-22T00:00:00Z"), at("2026-09-22T00:30:00Z"), SleepStageType.AWAKE_IN_BED),
            ),
        )
        val b = SleepSession(
            at("2026-09-22T02:00:00Z"), at("2026-09-22T05:00:00Z"),
            listOf(SleepStage(at("2026-09-22T02:00:00Z"), at("2026-09-22T03:00:00Z"), SleepStageType.REM)),
        )
        val n = SleepNightAttributor(london).attribute(listOf(a, b)).values.single()
        assertEquals(Duration.ofHours(6), n.total)
        assertEquals(Duration.ofHours(1), n.light)
        assertEquals(Duration.ofHours(1), n.deep)
        assertEquals(Duration.ofHours(1), n.rem)
        assertEquals(Duration.ofMinutes(30), n.awake)
        assertEquals(Duration.ofMinutes(150), n.unstaged) // 30 min in a, 2 h in b
        assertEquals(LocalTime.of(23, 0), n.bedtime.toLocalTime())
        assertEquals(LocalTime.of(6, 0), n.wake.toLocalTime())
    }

    @Test
    fun `mean clock time wraps midnight`() {
        assertEquals(LocalTime.MIDNIGHT, meanClockTime(listOf(LocalTime.of(23, 30), LocalTime.of(0, 30))))
        assertEquals(LocalTime.of(6, 35), meanClockTime(listOf(LocalTime.of(6, 30), LocalTime.of(6, 40))))
        assertNull(meanClockTime(emptyList()))
    }

    @Test
    fun `a missing night is reported as a gap, not a zero`() {
        val stats = sleepMonthStats(SampleData.month, SampleData.nights, SampleData.now.toLocalDate())
        assertEquals(listOf(LocalDate.of(2026, 9, 20)), stats.missing)
        assertEquals(LocalDate.of(2026, 9, 16), stats.worst?.night)
        assertEquals(Duration.ofMinutes(302), stats.worst?.total)
        assertEquals(LocalDate.of(2026, 9, 4), stats.best?.night) // 8:32
        assertNotNull(stats.mean)
    }

    @Test
    fun `rolling mean needs four nights and stops at today`() {
        val means = rollingMeanHours(SampleData.month, SampleData.nights, LocalDate.of(2026, 9, 22))
        assertNull(means[0]); assertNull(means[2]) // 1..3 nights so far
        assertNotNull(means[3])
        assertNotNull(means[19]) // night 20 is missing but its window still has 6
        assertNull(means[22]) // tomorrow
        assertEquals(30, means.size)
    }

    @Test
    fun `a future month expects nothing yet`() {
        val stats = sleepMonthStats(YearMonth.of(2026, 10), SampleData.nights, LocalDate.of(2026, 9, 22))
        assertNull(stats.through)
        assertEquals(emptyList<LocalDate>(), stats.missing)
    }

    @Test
    fun `formats`() {
        assertEquals("7:12", Formats.hoursColon(Duration.ofMinutes(432)))
        assertEquals("7h 12m", Formats.hoursMinutes(Duration.ofMinutes(432)))
        assertEquals("Tue 22", Formats.dayLabel(LocalDate.of(2026, 9, 22)))
        assertEquals("Mon–Fri", Formats.days(SampleData.schedules[1].daysOfWeek))
        assertEquals("Every day", Formats.days(SampleData.schedules[0].daysOfWeek))
        assertEquals("HealthNote-2026-09.pdf", Formats.documentFileName(SampleData.month))
    }
}

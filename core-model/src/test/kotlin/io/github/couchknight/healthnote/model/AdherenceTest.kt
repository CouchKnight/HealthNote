package io.github.couchknight.healthnote.model

import io.github.couchknight.healthnote.model.sample.SampleData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

class AdherenceTest {
    private val report = MonthReport(
        SampleData.month, SampleData.now, SampleData.schedules, SampleData.events, SampleData.nights,
    )

    private fun med(id: String) = report.meds.first { it.schedule.id == id }

    @Test
    fun `september 2026 has 74 slots for the design schedule set`() {
        // 30 daily Vyvanse + 22 weekdays x 2. The prototype's "96 slots" copy was wrong.
        assertEquals(30, med("vyv").slots.size)
        assertEquals(22, med("om3").slots.size)
        assertEquals(22, med("vc").slots.size)
        assertEquals(74, report.meds.sumOf { it.slots.size })
    }

    @Test
    fun `skips count on neither side of adherence`() {
        val vc = med("vc").counts
        assertEquals(AdherenceCounts(taken = 13, missed = 1, skipped = 1, due = 1, upcoming = 6), vc)
        assertEquals(93, vc.percent) // 13 / 14
    }

    @Test
    fun `overall month to date matches the sample`() {
        val o = report.overall
        assertEquals(48, o.taken)
        assertEquals(3, o.missed)
        assertEquals(1, o.skipped)
        assertEquals(94, o.percent) // 48 / 51
        assertEquals(22, o.remaining) // 2 due today + 20 later; the prototype said 44
    }

    @Test
    fun `an unlogged dose is due today and missed only once the day is over`() {
        val slot = DoseSlot("om3", LocalDateTime.of(2026, 9, 22, 8, 0))
        assertEquals(SlotState.UPCOMING, resolveSlot(slot, null, LocalDateTime.of(2026, 9, 22, 7, 59)))
        assertEquals(SlotState.DUE, resolveSlot(slot, null, LocalDateTime.of(2026, 9, 22, 23, 59)))
        assertEquals(SlotState.MISSED, resolveSlot(slot, null, LocalDateTime.of(2026, 9, 23, 0, 0)))
    }

    @Test
    fun `streaks run back from today and skip unscheduled days`() {
        assertEquals(7, med("vyv").streakDays) // missed Tue 15; 16..22 taken
        assertEquals(8, med("om3").streakDays) // missed Wed 9; today still due, so it neither counts nor breaks
        assertEquals(5, med("vc").streakDays) // missed Mon 14
        assertEquals(14, med("vyv").bestStreakDays) // 1..14
    }

    @Test
    fun `a day of only skipped slots neither counts nor breaks a streak`() {
        val s = MedicationSchedule(
            "x", "X", "1", listOf(LocalTime.of(8, 0)), DayOfWeek.entries.toSet(), LocalDate.of(2026, 1, 1),
        )
        val events = (1..5).map { d ->
            val at = LocalDateTime.of(2026, 1, d, 8, 0)
            DoseEvent("e$d", "x", at, at, if (d == 3) DoseStatus.SKIPPED else DoseStatus.TAKEN, DoseSource.MANUAL)
        }
        assertEquals(4, currentStreakDays(s, events, LocalDateTime.of(2026, 1, 5, 12, 0)))
    }

    @Test
    fun `percent is null before anything has resolved`() {
        assertNull(AdherenceCounts(0, 0, 2, 1, 5).percent)
    }

    @Test
    fun `last lapse is the latest miss or skip`() {
        assertEquals(15, med("vyv").lastLapse?.slot?.date?.dayOfMonth)
        assertEquals(SlotState.MISSED, med("vc").lastLapse?.state)
        assertEquals(14, med("vc").lastLapse?.slot?.date?.dayOfMonth)
    }

    @Test
    fun `schedules outside the month are dropped and the rest keep a stable order`() {
        val ended = SampleData.schedules[0].copy(id = "old", endDate = LocalDate.of(2026, 8, 31))
        val r = MonthReport(YearMonth.of(2026, 9), SampleData.now, SampleData.schedules + ended, emptyList(), emptyMap())
        assertEquals(listOf("vyv", "om3", "vc"), r.schedules.map { it.id })
    }
}

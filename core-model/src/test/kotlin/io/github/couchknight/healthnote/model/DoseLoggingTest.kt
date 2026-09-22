package io.github.couchknight.healthnote.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class DoseLoggingTest {
    private val twiceDaily = MedicationSchedule(
        "m", "M", "1", listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)), DayOfWeek.entries.toSet(),
        LocalDate.of(2026, 1, 1),
    )
    private val midnight = twiceDaily.copy(id = "n", timesOfDay = listOf(LocalTime.of(0, 15)))

    private fun repo() = InMemoryDoseRepository({ listOf(twiceDaily, midnight) })

    @Test
    fun `a log matches the nearest slot in the window`() {
        assertEquals(
            LocalDateTime.of(2026, 9, 22, 8, 0),
            matchSlot(twiceDaily, LocalDateTime.of(2026, 9, 22, 9, 31))?.scheduledFor,
        )
        assertEquals(
            LocalDateTime.of(2026, 9, 22, 20, 0),
            matchSlot(twiceDaily, LocalDateTime.of(2026, 9, 22, 17, 0))?.scheduledFor,
        )
        assertNull(matchSlot(twiceDaily, LocalDateTime.of(2026, 9, 22, 14, 0)))
    }

    @Test
    fun `a log before midnight can land on a slot just after it`() {
        assertEquals(
            LocalDateTime.of(2026, 9, 23, 0, 15),
            matchSlot(midnight, LocalDateTime.of(2026, 9, 22, 23, 50))?.scheduledFor,
        )
    }

    @Test
    fun `a double tap writes one event`() {
        val r = repo()
        val first = r.log("m", LocalDateTime.of(2026, 9, 22, 8, 2), DoseStatus.TAKEN, DoseSource.NFC)
        val second = r.log("m", LocalDateTime.of(2026, 9, 22, 8, 3), DoseStatus.TAKEN, DoseSource.NFC)
        assertInstanceOf(LogResult.Logged::class.java, first)
        assertInstanceOf(LogResult.AlreadyLogged::class.java, second)
        assertEquals((first as LogResult.Logged).event, (second as LogResult.AlreadyLogged).event)
        assertEquals(1, r.events().size)
    }

    @Test
    fun `unknown schedules and out-of-window logs are refused`() {
        val r = repo()
        assertEquals(LogResult.UnknownSchedule, r.log("zz", LocalDateTime.now(), DoseStatus.TAKEN, DoseSource.NFC))
        assertEquals(
            LogResult.NoSlotInWindow,
            r.log("m", LocalDateTime.of(2026, 9, 22, 14, 0), DoseStatus.TAKEN, DoseSource.NFC),
        )
    }

    @Test
    fun `tag uris round trip`() {
        assertEquals("healthnote://dose/vyv", doseTagUri("vyv"))
        assertEquals("vyv", scheduleIdFromTagUri("healthnote://dose/vyv"))
        assertNull(scheduleIdFromTagUri("https://example.com/vyv"))
        assertNull(scheduleIdFromTagUri("healthnote://dose/"))
    }
}

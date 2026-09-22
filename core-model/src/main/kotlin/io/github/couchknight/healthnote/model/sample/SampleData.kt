package io.github.couchknight.healthnote.model.sample

import io.github.couchknight.healthnote.model.DoseEvent
import io.github.couchknight.healthnote.model.DoseSource
import io.github.couchknight.healthnote.model.DoseStatus
import io.github.couchknight.healthnote.model.MedicationSchedule
import io.github.couchknight.healthnote.model.NightSleep
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

/**
 * The month the designs were drawn for: September 2026, seen at 09:31 on Tue 22.
 *
 * Stands in for Room and Health Connect until `:core-data` and `:core-health` exist, and pins
 * the renderer tests to the exact data in the design mockups.
 */
object SampleData {
    val month: YearMonth = YearMonth.of(2026, 9)
    val now: LocalDateTime = LocalDateTime.of(2026, 9, 22, 9, 31)

    private val weekdays = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )
    private val start = LocalDate.of(2026, 9, 1)

    val schedules: List<MedicationSchedule> = listOf(
        MedicationSchedule(
            "vyv", "Vyvanse", "30 mg", listOf(LocalTime.of(7, 0)), DayOfWeek.entries.toSet(), start, colorSlot = 0,
        ),
        MedicationSchedule("om3", "Omega 3", "1000 mg", listOf(LocalTime.of(8, 0)), weekdays, start, colorSlot = 1),
        MedicationSchedule("vc", "Vitamin C", "500 mg", listOf(LocalTime.of(8, 0)), weekdays, start, colorSlot = 2),
    )

    /** Exceptions from the design: everything else before now was taken. */
    private val missed = mapOf("vyv" to setOf(15), "om3" to setOf(9), "vc" to setOf(14))
    private val skipped = mapOf("vc" to setOf(3))

    private val SOURCES = listOf(DoseSource.WIDGET, DoseSource.NFC, DoseSource.NOTIFICATION, DoseSource.MANUAL)

    /** Omega 3 and Vitamin C are still due today; Vyvanse was logged from the widget at 06:58. */
    val events: List<DoseEvent> = buildList {
        for (s in schedules) {
            for (day in 1..now.dayOfMonth) {
                for (slot in s.slotsOn(month.atDay(day))) {
                    if (day in missed[s.id].orEmpty()) continue
                    if (day == now.dayOfMonth && s.id != "vyv") continue
                    val status = if (day in skipped[s.id].orEmpty()) DoseStatus.SKIPPED else DoseStatus.TAKEN
                    val offset = if (day == now.dayOfMonth) -2L else ((day * 7 + s.colorSlot * 5) % 23 - 6).toLong()
                    add(
                        DoseEvent(
                            id = "${s.id}-$day",
                            scheduleId = s.id,
                            scheduledFor = slot.scheduledFor,
                            loggedAt = slot.scheduledFor.plusMinutes(offset),
                            status = status,
                            source = if (day == now.dayOfMonth) DoseSource.WIDGET else SOURCES[day % SOURCES.size],
                        ),
                    )
                }
            }
        }
    }

    /** Night table from the design (page 4). Night 20 has not arrived from Samsung Health. */
    private val NIGHTS = listOf(
        "1 23:12 06:41 1:08 1:34 4:26 0:21",
        "2 23:48 06:35 0:54 1:22 4:12 0:19",
        "3 00:04 06:38 0:49 1:18 4:07 0:20",
        "4 23:20 07:52 1:14 1:47 5:11 0:20",
        "5 23:58 08:14 1:11 1:42 4:59 0:24",
        "6 23:35 07:20 1:05 1:36 4:44 0:20",
        "7 23:41 06:30 0:57 1:23 4:11 0:18",
        "8 00:22 06:34 0:44 1:11 3:58 0:19",
        "9 23:30 06:36 1:02 1:30 4:14 0:20",
        "10 23:54 06:30 0:52 1:19 4:05 0:20",
        "11 23:19 06:41 1:07 1:33 4:21 0:21",
        "12 00:31 06:43 0:45 1:10 3:58 0:19",
        "13 22:58 06:46 1:09 1:38 4:41 0:20",
        "14 23:47 06:41 0:58 1:25 4:12 0:19",
        "15 23:26 06:56 1:06 1:34 4:29 0:21",
        "16 01:22 06:24 0:38 0:54 3:14 0:16",
        "17 23:31 06:35 1:01 1:29 4:14 0:20",
        "18 23:55 06:40 0:56 1:21 4:09 0:19",
        "19 22:48 07:09 1:13 1:44 5:04 0:20",
        "21 00:10 06:32 0:47 1:14 4:02 0:19",
        "22 23:24 06:36 1:04 1:38 4:12 0:18",
    )

    val nights: Map<LocalDate, NightSleep> = NIGHTS.associate { line ->
        val p = line.split(" ")
        val night = month.atDay(p[0].toInt())
        val bedClock = LocalTime.parse(p[1])
        val bed = (if (bedClock.hour >= 12) night.minusDays(1) else night).atTime(bedClock)
        val wake = night.atTime(LocalTime.parse(p[2]))
        val deep = hm(p[3]); val rem = hm(p[4]); val light = hm(p[5]); val awake = hm(p[6])
        val total = Duration.between(bed, wake)
        night to NightSleep(
            night, bed, wake, total, deep, rem, light, awake,
            unstaged = (total - deep - rem - light - awake).coerceAtLeast(Duration.ZERO),
        )
    }

    private fun hm(s: String): Duration {
        val (h, m) = s.split(":").map { it.toLong() }
        return Duration.ofHours(h).plusMinutes(m)
    }

    private fun Duration.coerceAtLeast(min: Duration) = if (this < min) min else this
}

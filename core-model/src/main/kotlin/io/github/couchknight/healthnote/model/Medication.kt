package io.github.couchknight.healthnote.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** DESIGN.md §5.1. `colorSlot` picks the phone colour; e-ink ignores it (see §4.1 palette). */
data class MedicationSchedule(
    val id: String,
    val name: String,
    val dose: String,
    val timesOfDay: List<LocalTime>,
    val daysOfWeek: Set<DayOfWeek>,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val colorSlot: Int = 0,
    val active: Boolean = true,
    val remind: Boolean = true,
) {
    init {
        require(timesOfDay.isNotEmpty()) { "a schedule needs at least one time of day" }
    }

    fun isScheduledOn(date: LocalDate): Boolean =
        active &&
            date.dayOfWeek in daysOfWeek &&
            !date.isBefore(startDate) &&
            (endDate == null || !date.isAfter(endDate))

    fun slotsOn(date: LocalDate): List<DoseSlot> =
        if (!isScheduledOn(date)) {
            emptyList()
        } else {
            timesOfDay.sorted().map { DoseSlot(id, date.atTime(it)) }
        }
}

/** One scheduled dose: the unit adherence is counted in, and the key logging is idempotent on. */
data class DoseSlot(val scheduleId: String, val scheduledFor: LocalDateTime) {
    val date: LocalDate get() = scheduledFor.toLocalDate()
}

enum class DoseStatus { TAKEN, SKIPPED }

enum class DoseSource { WIDGET, NFC, NOTIFICATION, MANUAL }

data class DoseEvent(
    val id: String,
    val scheduleId: String,
    val scheduledFor: LocalDateTime,
    val loggedAt: LocalDateTime,
    val status: DoseStatus,
    val source: DoseSource,
) {
    val slot: DoseSlot get() = DoseSlot(scheduleId, scheduledFor)
}

/**
 * Where a slot stands at a given moment.
 *
 * - [MISSED] only once the slot's day has ended unresolved. A dose that is late today is [DUE],
 *   not missed, so the grid does not show a hollow marker at 08:05.
 * - [SKIPPED] is resolved but deliberately not taken; it is excluded from adherence entirely.
 */
enum class SlotState { TAKEN, SKIPPED, MISSED, DUE, UPCOMING }

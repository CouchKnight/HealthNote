package io.github.couchknight.healthnote.model

import java.time.Duration
import java.time.LocalDateTime

/**
 * Finds the slot a log belongs to: the nearest scheduled slot of that schedule within
 * [tolerance] of [at]. Slots on the previous and next day are considered, so a 23:50 log can
 * land on a 00:15 slot.
 */
fun matchSlot(
    schedule: MedicationSchedule,
    at: LocalDateTime,
    tolerance: Duration = DEFAULT_TOLERANCE,
): DoseSlot? {
    val day = at.toLocalDate()
    return listOf(day.minusDays(1), day, day.plusDays(1))
        .flatMap { schedule.slotsOn(it) }
        .map { it to Duration.between(it.scheduledFor, at).abs() }
        .filter { it.second <= tolerance }
        .minByOrNull { it.second }
        ?.first
}

val DEFAULT_TOLERANCE: Duration = Duration.ofHours(4)

sealed interface LogResult {
    data class Logged(val event: DoseEvent) : LogResult

    /** The slot already had an event; nothing was written. A second NFC tap lands here. */
    data class AlreadyLogged(val event: DoseEvent) : LogResult

    data object NoSlotInWindow : LogResult

    data object UnknownSchedule : LogResult
}

/** Storage-agnostic contract for dose events. Room implements it in `:core-data`. */
interface DoseRepository {
    fun events(): List<DoseEvent>

    /** Idempotent per slot (DESIGN.md §5.1): logging twice against one slot writes one event. */
    fun log(
        scheduleId: String,
        at: LocalDateTime,
        status: DoseStatus,
        source: DoseSource,
        slot: DoseSlot? = null,
    ): LogResult
}

class InMemoryDoseRepository(
    private val schedules: () -> List<MedicationSchedule>,
    initial: Collection<DoseEvent> = emptyList(),
    private val tolerance: Duration = DEFAULT_TOLERANCE,
    private val newId: () -> String = { java.util.UUID.randomUUID().toString() },
) : DoseRepository {
    private val bySlot = LinkedHashMap<DoseSlot, DoseEvent>().apply {
        initial.forEach { put(it.slot, it) }
    }

    @Synchronized
    override fun events(): List<DoseEvent> = bySlot.values.toList()

    @Synchronized
    override fun log(
        scheduleId: String,
        at: LocalDateTime,
        status: DoseStatus,
        source: DoseSource,
        slot: DoseSlot?,
    ): LogResult {
        val schedule = schedules().firstOrNull { it.id == scheduleId } ?: return LogResult.UnknownSchedule
        val target = slot ?: matchSlot(schedule, at, tolerance) ?: return LogResult.NoSlotInWindow
        bySlot[target]?.let { return LogResult.AlreadyLogged(it) }
        val event = DoseEvent(newId(), scheduleId, target.scheduledFor, at, status, source)
        bySlot[target] = event
        return LogResult.Logged(event)
    }
}

/** The URI an NFC tag carries (DESIGN.md §5.2). */
fun doseTagUri(scheduleId: String): String = "healthnote://dose/$scheduleId"

fun scheduleIdFromTagUri(uri: String): String? =
    uri.removePrefix("healthnote://dose/").takeIf { it != uri && it.isNotBlank() && '/' !in it }

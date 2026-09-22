package io.github.couchknight.healthnote.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

data class ResolvedSlot(val slot: DoseSlot, val state: SlotState, val event: DoseEvent?)

fun resolveSlot(slot: DoseSlot, event: DoseEvent?, now: LocalDateTime): SlotState {
    if (event != null) {
        return if (event.status == DoseStatus.TAKEN) SlotState.TAKEN else SlotState.SKIPPED
    }
    val today = now.toLocalDate()
    return when {
        slot.date.isBefore(today) -> SlotState.MISSED
        slot.date == today && !slot.scheduledFor.isAfter(now) -> SlotState.DUE
        else -> SlotState.UPCOMING
    }
}

/** Every slot of one schedule in one month, resolved against the logged events. */
fun resolveMonth(
    schedule: MedicationSchedule,
    month: YearMonth,
    events: Collection<DoseEvent>,
    now: LocalDateTime,
): List<ResolvedSlot> {
    val bySlot = events.filter { it.scheduleId == schedule.id }.associateBy { it.slot }
    return (1..month.lengthOfMonth())
        .flatMap { schedule.slotsOn(month.atDay(it)) }
        .map { ResolvedSlot(it, resolveSlot(it, bySlot[it], now), bySlot[it]) }
}

data class AdherenceCounts(
    val taken: Int,
    val missed: Int,
    val skipped: Int,
    val due: Int,
    val upcoming: Int,
) {
    /**
     * taken / (taken + missed). Skipped slots count on neither side: a deliberate skip is a
     * decision, not a lapse. Null until at least one slot has been taken or missed.
     */
    val fraction: Double? get() = (taken + missed).takeIf { it > 0 }?.let { taken.toDouble() / it }
    val percent: Int? get() = fraction?.let { Math.round(it * 100).toInt() }

    /** Slots not yet resolved: due now, later today, or on a future day. */
    val remaining: Int get() = due + upcoming

    operator fun plus(o: AdherenceCounts) = AdherenceCounts(
        taken + o.taken, missed + o.missed, skipped + o.skipped, due + o.due, upcoming + o.upcoming,
    )

    companion object {
        val ZERO = AdherenceCounts(0, 0, 0, 0, 0)

        fun of(slots: Iterable<ResolvedSlot>): AdherenceCounts {
            var t = 0; var m = 0; var s = 0; var d = 0; var u = 0
            for (r in slots) {
                when (r.state) {
                    SlotState.TAKEN -> t++
                    SlotState.MISSED -> m++
                    SlotState.SKIPPED -> s++
                    SlotState.DUE -> d++
                    SlotState.UPCOMING -> u++
                }
            }
            return AdherenceCounts(t, m, s, d, u)
        }
    }
}

/**
 * Consecutive scheduled days, counting back from today, on which every slot was taken.
 *
 * Unscheduled days are passed over. A day whose slots were all skipped neither counts nor
 * breaks the run, matching the adherence rule. Today counts only once it is complete; an
 * unresolved slot today does not break the run, because the day is not over.
 */
fun currentStreakDays(
    schedule: MedicationSchedule,
    events: Collection<DoseEvent>,
    now: LocalDateTime,
    lookBackDays: Long = 400,
): Int = streaks(schedule, events, now, lookBackDays).current

/** The longest run inside the look-back window, by the same rule as [currentStreakDays]. */
fun bestStreakDays(
    schedule: MedicationSchedule,
    events: Collection<DoseEvent>,
    now: LocalDateTime,
    lookBackDays: Long = 400,
): Int = streaks(schedule, events, now, lookBackDays).best

private data class Streaks(val current: Int, val best: Int)

private fun streaks(
    schedule: MedicationSchedule,
    events: Collection<DoseEvent>,
    now: LocalDateTime,
    lookBackDays: Long,
): Streaks {
    val bySlot = events.filter { it.scheduleId == schedule.id }.associateBy { it.slot }
    val today = now.toLocalDate()
    var first = today.minusDays(lookBackDays)
    if (first.isBefore(schedule.startDate)) first = schedule.startDate
    var run = 0
    var best = 0
    var d: LocalDate = first
    while (!d.isAfter(today)) {
        val states = schedule.slotsOn(d).map { resolveSlot(it, bySlot[it], now) }
        when {
            states.isEmpty() -> Unit
            SlotState.MISSED in states -> run = 0
            states.all { it == SlotState.TAKEN || it == SlotState.SKIPPED } ->
                if (SlotState.TAKEN in states) run++
            else -> Unit // today, still open
        }
        if (run > best) best = run
        d = d.plusDays(1)
    }
    return Streaks(run, best)
}

/** The latest missed or skipped slot up to now, for the "missed Tue 15" line. */
fun lastLapse(slots: List<ResolvedSlot>): ResolvedSlot? =
    slots.lastOrNull { it.state == SlotState.MISSED || it.state == SlotState.SKIPPED }

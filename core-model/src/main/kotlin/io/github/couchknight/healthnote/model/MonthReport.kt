package io.github.couchknight.healthnote.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/** Everything the document and the phone screens show for one month, computed once. */
class MonthReport(
    val month: YearMonth,
    val now: LocalDateTime,
    schedules: List<MedicationSchedule>,
    val events: List<DoseEvent>,
    val nights: Map<LocalDate, NightSleep>,
    val target: SleepTarget = SleepTarget(),
) {
    val today: LocalDate = now.toLocalDate()

    /** Schedules that have at least one slot this month, in display order. */
    val schedules: List<MedicationSchedule> = orderSchedules(
        schedules.filter { s -> (1..month.lengthOfMonth()).any { s.isScheduledOn(month.atDay(it)) } },
    )

    val meds: List<MedReport> = this.schedules.map { s ->
        val slots = resolveMonth(s, month, events, now)
        MedReport(
            schedule = s,
            slots = slots,
            counts = AdherenceCounts.of(slots),
            streakDays = currentStreakDays(s, events, now),
            bestStreakDays = bestStreakDays(s, events, now),
            lastLapse = lastLapse(slots),
        )
    }

    val overall: AdherenceCounts = meds.fold(AdherenceCounts.ZERO) { acc, m -> acc + m.counts }

    val sleep: SleepMonthStats = sleepMonthStats(month, nights, today)

    val rollingMeanHours: List<Double?> = rollingMeanHours(month, nights, sleep.through)

    /** Most recent night on or before today, if any. */
    val lastNight: NightSleep? = nights.values.filter { !it.night.isAfter(today) }.maxByOrNull { it.night }

    /** Today's slots across all schedules, in time order. */
    val todaySlots: List<Pair<MedReport, ResolvedSlot>> =
        meds.flatMap { m -> m.slots.filter { it.slot.date == today }.map { m to it } }
            .sortedWith(compareBy({ it.second.slot.scheduledFor }, { schedules.indexOf(it.first.schedule) }))

    fun state(schedule: MedicationSchedule, day: Int): List<SlotState> =
        meds.first { it.schedule.id == schedule.id }.slots
            .filter { it.slot.date.dayOfMonth == day }
            .map { it.state }

    companion object {
        /** Earliest dose first, then colour slot, then name: stable across regenerations. */
        fun orderSchedules(list: List<MedicationSchedule>): List<MedicationSchedule> =
            list.sortedWith(compareBy({ it.timesOfDay.min() }, { it.colorSlot }, { it.name }))
    }
}

data class MedReport(
    val schedule: MedicationSchedule,
    val slots: List<ResolvedSlot>,
    val counts: AdherenceCounts,
    val streakDays: Int,
    val bestStreakDays: Int,
    val lastLapse: ResolvedSlot?,
)

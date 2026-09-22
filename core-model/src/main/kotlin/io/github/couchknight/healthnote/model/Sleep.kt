package io.github.couchknight.healthnote.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/** Mirrors `SleepSessionRecord.STAGE_TYPE_*` in Health Connect. */
enum class SleepStageType { UNKNOWN, AWAKE, SLEEPING, OUT_OF_BED, LIGHT, DEEP, REM, AWAKE_IN_BED }

data class SleepStage(val start: Instant, val end: Instant, val type: SleepStageType)

/** A Health Connect `SleepSessionRecord`, reduced to what attribution needs. */
data class SleepSession(
    val start: Instant,
    val end: Instant,
    val stages: List<SleepStage> = emptyList(),
)

/** One calendar night after attribution and merging (DESIGN.md §3). */
data class NightSleep(
    /** The date this night is filed under: the wake date by default. */
    val night: LocalDate,
    val bedtime: LocalDateTime,
    val wake: LocalDateTime,
    /** Sum of session durations, including time awake inside a session. */
    val total: Duration,
    val deep: Duration,
    val rem: Duration,
    val light: Duration,
    val awake: Duration,
    /** Stage-less or `SLEEPING` time: asleep, but the watch did not say which stage. */
    val unstaged: Duration = Duration.ZERO,
)

enum class NightAttribution {
    /** The local date of `endTime`: a night that ends Tuesday morning is Tuesday's. */
    WAKE_DATE,

    /** The local date of `startTime`, for people who think of a night by when it began. */
    BED_DATE,
}

/**
 * Maps sessions to calendar nights (DESIGN.md §3). Several sessions on one night (a nap is not
 * special-cased; filter upstream if needed) are merged: bedtime is the earliest start, wake the
 * latest end, and durations are summed per stage. Durations are computed on instants, so a
 * night across a DST change reports the time actually slept.
 */
class SleepNightAttributor(
    private val zone: ZoneId,
    private val attribution: NightAttribution = NightAttribution.WAKE_DATE,
) {
    fun attribute(sessions: Collection<SleepSession>): Map<LocalDate, NightSleep> =
        sessions
            .groupBy {
                val key = if (attribution == NightAttribution.WAKE_DATE) it.end else it.start
                key.atZone(zone).toLocalDate()
            }
            .mapValues { (night, group) -> merge(night, group) }
            .toSortedMap()

    private fun merge(night: LocalDate, group: List<SleepSession>): NightSleep {
        var total = Duration.ZERO
        var deep = Duration.ZERO
        var rem = Duration.ZERO
        var light = Duration.ZERO
        var awake = Duration.ZERO
        var unstaged = Duration.ZERO
        for (s in group) {
            val length = Duration.between(s.start, s.end)
            total += length
            var staged = Duration.ZERO
            for (st in s.stages) {
                val d = Duration.between(st.start, st.end)
                staged += d
                when (st.type) {
                    SleepStageType.DEEP -> deep += d
                    SleepStageType.REM -> rem += d
                    SleepStageType.LIGHT -> light += d
                    SleepStageType.AWAKE, SleepStageType.AWAKE_IN_BED, SleepStageType.OUT_OF_BED -> awake += d
                    SleepStageType.SLEEPING, SleepStageType.UNKNOWN -> unstaged += d
                }
            }
            if (length > staged) unstaged += length - staged
        }
        return NightSleep(
            night = night,
            bedtime = group.minOf { it.start }.atZone(zone).toLocalDateTime(),
            wake = group.maxOf { it.end }.atZone(zone).toLocalDateTime(),
            total = total,
            deep = deep,
            rem = rem,
            light = light,
            awake = awake,
            unstaged = unstaged,
        )
    }
}

data class SleepTarget(val minHours: Double = 7.0, val maxHours: Double = 9.0) {
    fun contains(d: Duration): Boolean = d.toMinutes() / 60.0 in minHours..maxHours
}

/** Month summary for the sleep page and the phone's sleep screen. */
data class SleepMonthStats(
    val mean: Duration?,
    val meanBedtime: LocalTime?,
    val meanWake: LocalTime?,
    val best: NightSleep?,
    val worst: NightSleep?,
    /** Nights up to [through] with no data: rendered as gaps, never as zero (DESIGN.md §3). */
    val missing: List<LocalDate>,
    /** Last night that was expected to have data: today, or the month end for a past month. */
    val through: LocalDate?,
)

fun sleepMonthStats(month: YearMonth, nights: Map<LocalDate, NightSleep>, today: LocalDate): SleepMonthStats {
    val inMonth = nights.values.filter { YearMonth.from(it.night) == month }.sortedBy { it.night }
    val through = when {
        month.atDay(1).isAfter(today) -> null
        month.atEndOfMonth().isBefore(today) -> month.atEndOfMonth()
        else -> today
    }
    val missing = if (through == null) {
        emptyList()
    } else {
        (1..through.dayOfMonth).map { month.atDay(it) }.filter { it !in nights }
    }
    if (inMonth.isEmpty()) return SleepMonthStats(null, null, null, null, null, missing, through)
    val meanSeconds = inMonth.map { it.total.seconds }.average()
    return SleepMonthStats(
        mean = Duration.ofSeconds(Math.round(meanSeconds)),
        meanBedtime = meanClockTime(inMonth.map { it.bedtime.toLocalTime() }),
        meanWake = meanClockTime(inMonth.map { it.wake.toLocalTime() }),
        best = inMonth.maxBy { it.total },
        worst = inMonth.minBy { it.total },
        missing = missing,
        through = through,
    )
}

/**
 * Mean of clock times that may straddle midnight. Times are measured from noon, so 23:30 and
 * 00:30 average to 00:00 rather than 12:00. Assumes the times sit within 12 hours of each other.
 */
fun meanClockTime(times: List<LocalTime>): LocalTime? {
    if (times.isEmpty()) return null
    val fromNoon = times.map { ((it.toSecondOfDay() - 12 * 3600) + 86_400) % 86_400 }
    val mean = Math.round(fromNoon.average())
    return LocalTime.ofSecondOfDay(((mean + 12 * 3600) % 86_400))
}

/**
 * Trailing mean of nightly hours for each day of the month: the 7 nights ending that day,
 * shown only when at least [minNights] of them have data (matches the design's chart).
 * Index 0 is day 1. Days after [through] are null.
 */
fun rollingMeanHours(
    month: YearMonth,
    nights: Map<LocalDate, NightSleep>,
    through: LocalDate?,
    window: Int = 7,
    minNights: Int = 4,
): List<Double?> =
    (1..month.lengthOfMonth()).map { day ->
        val date = month.atDay(day)
        if (through == null || date.isAfter(through)) return@map null
        val values = (0 until window).mapNotNull { back ->
            val d = date.minusDays(back.toLong())
            if (YearMonth.from(d) != month) null else nights[d]?.total?.let { it.seconds / 3600.0 }
        }
        if (values.size < minNights) null else values.average()
    }

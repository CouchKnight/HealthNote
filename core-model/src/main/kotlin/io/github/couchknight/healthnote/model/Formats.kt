package io.github.couchknight.healthnote.model

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Text formats shared by the phone UI and the document, so both say the same thing. */
object Formats {
    private val locale: Locale = Locale.UK

    /** 7:12 */
    fun hoursColon(d: Duration): String = "%d:%02d".format(d.toHours(), d.toMinutes() % 60)

    /** 7h 12m */
    fun hoursMinutes(d: Duration): String = "%dh %02dm".format(d.toHours(), d.toMinutes() % 60)

    /** 06:36 */
    fun clock(t: LocalTime): String = "%02d:%02d".format(t.hour, t.minute)

    /** 6:36, used where a column is too narrow for the leading zero */
    fun clockShort(t: LocalTime): String = "%d:%02d".format(t.hour, t.minute)

    /** Tue */
    fun weekday(d: DayOfWeek): String = d.getDisplayName(TextStyle.SHORT, locale).take(3)

    /** Tue 22 */
    fun dayLabel(d: LocalDate): String = "${weekday(d.dayOfWeek)} ${d.dayOfMonth}"

    /** Tue 22 Sep */
    fun dayMonthLabel(d: LocalDate): String =
        "${dayLabel(d)} ${d.month.getDisplayName(TextStyle.SHORT, locale).take(3)}"

    /** September 2026 */
    fun monthTitle(m: YearMonth): String =
        "${m.month.getDisplayName(TextStyle.FULL, locale)} ${m.year}"

    /** September */
    fun monthName(m: YearMonth): String = m.month.getDisplayName(TextStyle.FULL, locale)

    /** HealthNote-2026-09.pdf: one immutable-geometry file per month (DESIGN.md §1.3). */
    fun documentFileName(m: YearMonth): String = "HealthNote-%04d-%02d.pdf".format(m.year, m.monthValue)

    /** Every day · Mon–Fri · Mon, Wed, Fri */
    fun days(days: Set<DayOfWeek>): String {
        val sorted = days.sorted()
        return when {
            sorted.size == 7 -> "Every day"
            sorted.isEmpty() -> "No days"
            sorted.size > 2 && sorted.zipWithNext().all { (a, b) -> b.value == a.value + 1 } ->
                "${weekday(sorted.first())}–${weekday(sorted.last())}"
            else -> sorted.joinToString(", ") { weekday(it) }
        }
    }

    fun times(times: List<LocalTime>): String = times.sorted().joinToString(", ") { clock(it) }
}

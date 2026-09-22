package io.github.couchknight.healthnote.render

import io.github.couchknight.healthnote.model.Formats
import io.github.couchknight.healthnote.model.MonthReport
import io.github.couchknight.healthnote.model.NightSleep
import java.time.Duration
import kotlin.math.floor
import kotlin.math.min

/**
 * Page 3 (design 1d): nightly hours on a fixed 1..31 x 0..12 h frame, a shaded target band,
 * the nightly line broken at missing nights and a dashed 7-day mean.
 */
internal class SleepChartPage(private val cfg: RenderConfig) {
    val frame = Layer()
    val links = mutableListOf<Link>()

    private val x0: Float
    private val plotW: Float
    private val plotTop: Float
    private val plotH: Float
    private val statsValueTop: Float
    private val statsColX: List<Float>
    private val noteTop: Float

    private val value = Style(Face.CASLON_BOLD, 74f)

    fun x(day: Int) = x0 + (day - 0.5f) / DAYS * plotW
    fun y(hours: Double) = plotTop + (1f - (hours.toFloat().coerceIn(0f, YMAX) / YMAX)) * plotH

    init {
        val top = Chrome.header(frame, "Sleep", "${Formats.monthTitle(cfg.month)} · hours per night")
        val chartTop = Chrome.caption(
            frame,
            "Fixed 1–31 axis and fixed 0–12 h scale for the whole month. The line breaks at a " +
                "missing night; it is never drawn as zero.",
            top, 26f, 36f,
        )
        val chartH = 700f
        val axisX = Page.LEFT + 70f + 20f
        x0 = axisX + 3f
        plotW = Page.RIGHT - x0
        plotTop = chartTop
        plotH = chartH - 3f

        // Target band, then the even-hour gridlines over it, then the axes.
        val t = cfg.target
        val bandTop = y(t.maxHours)
        val bandBottom = y(t.minHours)
        frame.rect(x0, bandTop, plotW, bandBottom - bandTop, Ink.LIGHT)
        frame.hrule(x0, bandTop, plotW, ink = Ink.DARK)
        frame.hrule(x0, bandBottom - Page.HAIRLINE, plotW, ink = Ink.DARK)
        for (h in 2..10 step 2) frame.hrule(x0, y(h.toDouble()) - 1f, plotW)
        frame.vrule(axisX, plotTop, chartH, 3f, Ink.BLACK)
        frame.hrule(axisX, plotTop + plotH, Page.RIGHT - axisX, 3f, Ink.BLACK)

        // Axis labels centred on their gridlines and days (the mockup spaced them evenly instead).
        for (h in 0..12 step 2) {
            frame.text(Styles.caption, h.toString(), Page.LEFT + 70f, y(h.toDouble()) - 15f, Align.RIGHT)
        }
        val xLabelsTop = plotTop + chartH + 14f
        for (d in listOf(1, 5, 10, 15, 20, 25, cfg.month.lengthOfMonth())) {
            frame.text(Styles.caption, d.toString(), x(d) - 50f, xLabelsTop, Align.CENTER, 100f)
        }

        // Legend.
        val legendTop = xLabelsTop + 30f + 26f
        var lx = axisX
        val mid = legendTop + 15f
        frame.rect(lx, mid - 2f, 44f, 4f, Ink.BLACK)
        lx += 56f
        lx += frame.text(Styles.caption, "nightly total", lx, legendTop) + 34f
        var dx = 0f
        while (dx < 44f) {
            frame.rect(lx + dx, mid - 2f, min(10f, 44f - dx), 4f, Ink.BLACK)
            dx += 18f
        }
        lx += 56f
        lx += frame.text(Styles.caption, "7-day mean", lx, legendTop) + 34f
        frame.rect(lx, mid - 11f, 44f, 22f, Ink.LIGHT)
        frame.hrule(lx, mid - 11f, 44f, ink = Ink.DARK)
        frame.hrule(lx, mid + 11f - Page.HAIRLINE, 44f, ink = Ink.DARK)
        lx += 56f
        frame.text(Styles.caption, "target ${hours(t.minHours)}–${hours(t.maxHours)} h", lx, legendTop)

        // Summary band: Mean | Bed / wake | Best | Worst at flex 1 : 1.4 : 1 : 1.
        val statsTop = legendTop + 30f + 56f
        frame.hrule(Page.LEFT, statsTop, Page.CONTENT_W, 3f, Ink.BLACK)
        val pad = 34f
        val flex = listOf(1f, 1.4f, 1f, 1f)
        val free = Page.CONTENT_W - 3 * (pad + Page.HAIRLINE)
        val inner = statsTop + 3f
        val innerH = 32f + 30f + 12f + value.size + 32f
        val xs = mutableListOf<Float>()
        var cx = Page.LEFT
        flex.forEachIndexed { i, f ->
            if (i > 0) {
                frame.vrule(cx, inner, innerH)
                cx += Page.HAIRLINE + pad
            }
            xs += cx
            cx += free * f / flex.sum()
        }
        statsColX = xs
        val statLabel = Styles.label.copy(tracking = .1f)
        listOf("Mean", "Bed / wake", "Best", "Worst").forEachIndexed { i, s -> frame.text(statLabel, s, xs[i], inner + 32f) }
        statsValueTop = inner + 32f + 30f + 12f
        frame.hrule(Page.LEFT, inner + innerH, Page.CONTENT_W, 3f, Ink.BLACK)
        noteTop = inner + innerH + 3f + 22f

        Chrome.footer(frame, links, Section.SLEEP)
    }

    fun paint(r: MonthReport): Layer = Layer().apply {
        val s = r.sleep
        val through = s.through?.dayOfMonth ?: 0
        val hours = (1..through).map { d -> r.nights[cfg.month.atDay(d)]?.let { it.total.seconds / 3600.0 } }

        // 7-day mean under the nightly line.
        val mean = r.rollingMeanHours.mapIndexedNotNull { i, h -> h?.let { Pt(x(i + 1), y(it)) } }
        if (mean.size > 1) ops += Op.Polyline(mean, 3f, Ink.BLACK, dash = listOf(14f, 10f))

        var run = mutableListOf<Pt>()
        fun flush() {
            if (run.size > 1) ops += Op.Polyline(run, 5f, Ink.BLACK)
            run = mutableListOf()
        }
        hours.forEachIndexed { i, h -> if (h == null) flush() else run += Pt(x(i + 1), y(h)) }
        flush()
        hours.forEachIndexed { i, h -> if (h != null) ops += Op.Circle(x(i + 1), y(h), 7f, Ink.BLACK) }

        // A missing night is absence, not a reading: a faint dashed column and a baseline tick.
        for (d in s.missing) {
            val gx = x(d.dayOfMonth)
            ops += Op.Line(gx, y(0.0), gx, y(YMAX.toDouble()), 3f, Ink.MID, listOf(8f, 8f))
            ops += Op.Line(gx, y(0.0), gx, y(0.0) - 22f, 5f, Ink.DARK)
        }

        val dash = "—"
        text(value, s.mean?.let { Formats.hoursColon(it) } ?: dash, statsColX[0], statsValueTop)
        val bedWake = if (s.meanBedtime != null && s.meanWake != null) {
            "${Formats.clock(s.meanBedtime!!)} / ${Formats.clockShort(s.meanWake!!)}"
        } else {
            dash
        }
        text(value.copy(size = 62f), bedWake, statsColX[1], statsValueTop)
        text(value, s.best?.let { Formats.hoursColon(it.total) } ?: dash, statsColX[2], statsValueTop)
        text(value, s.worst?.let { Formats.hoursColon(it.total) } ?: dash, statsColX[3], statsValueTop)

        if (s.missing.isNotEmpty()) {
            val days = s.missing.map { Formats.dayLabel(it) }
            val note = when (days.size) {
                1 -> "${days[0]} has not arrived from Samsung Health yet."
                2 -> "${days[0]} and ${days[1]} have not arrived from Samsung Health yet."
                else -> "${days.size} nights have not arrived from Samsung Health yet: ${days.joinToString(", ")}."
            }
            paragraph(Styles.caption, note, Page.LEFT, noteTop, Page.CONTENT_W, 2)
        }
    }

    private fun hours(h: Double) = if (h % 1.0 == 0.0) h.toInt().toString() else h.toString()

    companion object {
        /** The x axis is 31 days wide in every month, so the frame is identical month to month. */
        const val DAYS = 31
        const val YMAX = 12f
    }
}

/** Page 4 (design 1f): one row per night of the month, stage breakdown included. */
internal class SleepDetailPage(private val cfg: RenderConfig) {
    val frame = Layer()
    val links = mutableListOf<Link>()

    private val rowsTop: Float
    private val rowH: Float
    private val row: Style
    private val meanTop: Float

    /** Date, Bed, Wake, Total, then four right-aligned stage columns. */
    private val colX: List<Float>
    private val stageRight: List<Float>

    init {
        val top = Chrome.header(frame, "Sleep detail", Formats.monthTitle(cfg.month))
        val headTop = Chrome.caption(
            frame,
            "Stages summed from the raw session records, not the aggregate — the breakdown is what " +
                "this page exists for.",
            top, 26f, 30f,
        )
        val fixed = listOf(150f, 150f, 150f, 170f)
        val flexW = (Page.CONTENT_W - fixed.sum()) / 4
        colX = fixed.runningFold(Page.LEFT) { acc, w -> acc + w }.dropLast(1)
        stageRight = (1..4).map { Page.LEFT + fixed.sum() + it * flexW }

        val head = Style(Face.FRANKLIN_SEMIBOLD, 30f, Ink.DARK, tracking = .06f, upper = true)
        listOf("Date", "Bed", "Wake", "Total").forEachIndexed { i, s -> frame.text(head, s, colX[i], headTop) }
        listOf("Deep", "REM", "Light", "Awake").forEachIndexed { i, s ->
            frame.text(head, s, stageRight[i], headTop, Align.RIGHT)
        }
        frame.hrule(Page.LEFT, headTop + 30f + 16f, Page.CONTENT_W, 3f, Ink.BLACK)
        rowsTop = headTop + 30f + 16f + 3f

        // Every night of the month gets a row from day one, so rows never shift as nights arrive.
        // The design showed only nights so far, which would move the mean row daily.
        val meanH = 3f + 20f + 34f
        val days = cfg.month.lengthOfMonth()
        rowH = min(56f, floor((Page.FOOTER_TOP - 30f - meanH - rowsTop) / days))
        row = Style(Face.FRANKLIN_LIGHT, min(32f, rowH - Page.HAIRLINE - 12f))
        val date = row.copy(face = Face.FRANKLIN_REGULAR)
        for (d in 1..days) {
            val y = rowsTop + (d - 1) * rowH
            frame.text(date, Formats.dayLabel(cfg.month.atDay(d)), colX[0], textTop(y))
            frame.hrule(Page.LEFT, y + rowH - Page.HAIRLINE, Page.CONTENT_W)
        }
        val meanRule = rowsTop + days * rowH
        frame.hrule(Page.LEFT, meanRule, Page.CONTENT_W, 3f, Ink.BLACK)
        meanTop = meanRule + 3f + 20f
        frame.text(Style(Face.FRANKLIN_SEMIBOLD, 34f), "Mean", colX[0], meanTop)

        Chrome.footer(frame, links, Section.DETAIL)
    }

    private fun textTop(rowY: Float) = rowY + (rowH - Page.HAIRLINE - row.lineBox) / 2

    fun paint(r: MonthReport): Layer = Layer().apply {
        val through = r.sleep.through?.dayOfMonth ?: 0
        val dim = row.with(Ink.DARK)
        for (d in 1..through) {
            val top = textTop(rowsTop + (d - 1) * rowH)
            val n = r.nights[cfg.month.atDay(d)]
            if (n == null) {
                for (i in 1..3) text(dim, "—", colX[i], top)
                stageRight.forEach { text(dim, "—", it, top, Align.RIGHT) }
                continue
            }
            cells(n, top, row, row.copy(face = Face.CASLON_REGULAR))
        }

        val nights = r.nights.values.filter { it.night.year == cfg.month.year && it.night.month == cfg.month.month }
        if (nights.isEmpty()) return@apply
        val s = r.sleep
        val mean = Style(Face.FRANKLIN_REGULAR, 34f)
        text(mean, s.meanBedtime?.let { Formats.clock(it) } ?: "", colX[1], meanTop)
        text(mean, s.meanWake?.let { Formats.clock(it) } ?: "", colX[2], meanTop)
        text(mean.copy(face = Face.CASLON_BOLD), s.mean?.let { Formats.hoursColon(it) } ?: "", colX[3], meanTop)
        listOf<(NightSleep) -> Duration>({ it.deep }, { it.rem }, { it.light }, { it.awake }).forEachIndexed { i, f ->
            val avg = Duration.ofSeconds(nights.map { f(it).seconds }.average().toLong())
            text(mean, Formats.hoursColon(avg), stageRight[i], meanTop, Align.RIGHT)
        }
    }

    private fun Layer.cells(n: NightSleep, top: Float, style: Style, total: Style) {
        text(style, Formats.clock(n.bedtime.toLocalTime()), colX[1], top)
        text(style, Formats.clock(n.wake.toLocalTime()), colX[2], top)
        text(total, Formats.hoursColon(n.total), colX[3], top)
        listOf(n.deep, n.rem, n.light, n.awake).forEachIndexed { i, d ->
            text(style, Formats.hoursColon(d), stageRight[i], top, Align.RIGHT)
        }
    }
}

/**
 * Page 5: a static ruled page (DESIGN.md §4.2), identical on every regeneration, so there is
 * always somewhere safe to write. Not in the mockups; drawn in the same chrome.
 */
internal class NotesPage(cfg: RenderConfig) {
    val frame = Layer()
    val links = mutableListOf<Link>()

    init {
        val top = Chrome.header(frame, "Notes", Formats.monthTitle(cfg.month))
        var y = top + 88f
        while (y < Page.FOOTER_TOP - 40f) {
            frame.hrule(Page.LEFT, y, Page.CONTENT_W)
            y += 88f
        }
        Chrome.footer(frame, links, Section.NOTES)
    }
}

package io.github.couchknight.healthnote.render

import io.github.couchknight.healthnote.model.Formats
import io.github.couchknight.healthnote.model.MonthReport
import io.github.couchknight.healthnote.model.SlotState
import java.time.YearMonth

/** Page 1 (design 1a): today at a glance plus large links to each section. */
internal class HubPage(private val cfg: RenderConfig) {
    val frame = Layer()
    val links = mutableListOf<Link>()

    private val generatedTop: Float
    private val colX: List<Float>
    private val valueTop: Float
    private val subTop: Float
    private val rowTops: List<Float>

    private val value = Style(Face.CASLON_BOLD, 128f, tracking = -.03f)
    private val sub = Style(Face.FRANKLIN_LIGHT, 30f, Ink.DARK)
    private val generated = Style(Face.FRANKLIN_LIGHT, 32f, Ink.DARK)
    private val rowName = Style(Face.FRANKLIN_REGULAR, 44f)
    private val rowRight = Style(Face.FRANKLIN_LIGHT, 40f, Ink.DARK)

    init {
        val afterHeader = Chrome.header(
            frame, Formats.monthTitle(cfg.month), "HealthNote", titleSize = 92f,
            rightStyle = Style(Face.FRANKLIN_MEDIUM, 34f, Ink.BLACK, tracking = .14f, upper = true),
        )
        generatedTop = afterHeader + 22f

        // Three stat columns between 3-unit rules; 2-unit separators, 38 left padding after each.
        val statsTop = generatedTop + generated.lineBox + 46f
        frame.hrule(Page.LEFT, statsTop, Page.CONTENT_W, 3f, Ink.BLACK)
        val inner = statsTop + 3f
        val pad = 38f
        val colW = (Page.CONTENT_W - 2 * Page.HAIRLINE - 2 * pad) / 3
        colX = listOf(Page.LEFT, Page.LEFT + colW + Page.HAIRLINE + pad, Page.LEFT + 2 * (colW + Page.HAIRLINE + pad))
        val statsH = 34f + 30f + 14f + value.size + 10f + sub.lineBox + 38f
        frame.vrule(Page.LEFT + colW, inner, statsH)
        frame.vrule(colX[1] + colW, inner, statsH)
        listOf("Adherence", "Last night", "Streak").forEachIndexed { i, s ->
            frame.text(Styles.label, s, colX[i], inner + 34f)
        }
        valueTop = inner + 34f + 30f + 14f
        subTop = valueTop + value.size + 10f
        frame.hrule(Page.LEFT, inner + statsH, Page.CONTENT_W, 3f, Ink.BLACK)

        // Today's doses: a fixed number of ruled rows, so the rows never move as weekdays and
        // weekends change how many doses a day has.
        val labelTop = inner + statsH + 3f + 54f
        frame.text(Styles.label, "Today", Page.LEFT, labelTop)
        val rowsTop = labelTop + 30f + 22f
        val rowPitch = Page.HAIRLINE + 26f + rowName.lineBox + 26f
        val rowCount = todayRowCount(cfg)
        rowTops = (0 until rowCount).map { r ->
            val y = rowsTop + r * rowPitch
            frame.hrule(Page.LEFT, y, Page.CONTENT_W)
            y + Page.HAIRLINE + 26f
        }
        val rowsBottom = rowsTop + rowCount * rowPitch
        frame.hrule(Page.LEFT, rowsBottom, Page.CONTENT_W)

        // Section tiles, 2 x 2, each a link.
        val tilesTop = rowsBottom + Page.HAIRLINE + 56f
        val gap = 24f
        val tileW = (Page.CONTENT_W - gap) / 2
        val tileTitle = Style(Face.CASLON_BOLD, 48f)
        val tileSub = Style(Face.FRANKLIN_LIGHT, 32f, Ink.DARK, lineHeight = 1.35f)
        val subW = tileW - 2 * (3f + 34f)
        val tiles = listOf(
            Triple(
                Section.MEDS, "Habit tracker",
                if (cfg.usesStrip) "One row per medication · page 2" else "Month grid · per-med rail · page 2",
            ),
            Triple(Section.SLEEP, "Sleep", "Month chart · page 3"),
            Triple(Section.DETAIL, "Sleep detail", "Night by night · page 4"),
            Triple(Section.NOTES, "Notes", "Ruled, byte-identical · page 5"),
        )
        val subLines = tiles.maxOf { lineCount(tileSub, it.third, subW) }
        val tileH = 3f + 36f + tileTitle.size + 12f + subLines * tileSub.lineBox + 36f + 3f
        tiles.forEachIndexed { i, (section, title, subtitle) ->
            val x = Page.LEFT + (i % 2) * (tileW + gap)
            val y = tilesTop + (i / 2) * (tileH + gap)
            frame.border(x, y, tileW, tileH, 3f, Ink.BLACK)
            frame.text(tileTitle, title, x + 37f, y + 39f)
            frame.paragraph(tileSub, subtitle, x + 37f, y + 39f + tileTitle.size + 12f, subW)
            links += Link(Rect(x, y, tileW, tileH), section.ordinal)
        }

        Chrome.footer(frame, links, Section.HUB)
    }

    fun paint(r: MonthReport): Layer = Layer().apply {
        val inMonth = YearMonth.from(r.today) == cfg.month
        val sleepThrough = r.lastNight?.let { "sleep through ${Formats.dayLabel(it.night)}" } ?: "no sleep yet"
        val dosesThrough = if (inMonth) "doses through today" else "month complete"
        text(
            generated,
            "Generated ${Formats.dayMonthLabel(r.today)} ${Formats.clock(r.now.toLocalTime())} · $sleepThrough · $dosesThrough",
            Page.LEFT, generatedTop,
        )

        val o = r.overall
        text(value, o.percent?.let { "$it%" } ?: "—", colX[0], valueTop)
        text(sub, if (o.taken + o.missed > 0) "${o.taken} of ${o.taken + o.missed} doses" else "nothing logged yet", colX[0], subTop)

        val night = r.lastNight
        text(value, night?.let { Formats.hoursColon(it.total) } ?: "—", colX[1], valueTop)
        text(
            sub,
            // En dash, not the mockup's arrow: neither bundled family has U+2192.
            night?.let { "${Formats.clock(it.bedtime.toLocalTime())}–${Formats.clock(it.wake.toLocalTime())}" }
                ?: "no night yet",
            colX[1], subTop,
        )

        val primary = r.meds.firstOrNull()
        text(value, primary?.streakDays?.toString() ?: "—", colX[2], valueTop)
        text(sub, primary?.let { "days, ${it.schedule.name}" } ?: "no schedules", colX[2], subTop)

        if (!inMonth) return@apply
        val today = r.todaySlots
        val shown = if (today.size > rowTops.size) today.take(rowTops.size - 1) else today
        shown.forEachIndexed { i, (med, slot) ->
            val top = rowTops[i]
            val mark = top + (rowName.lineBox - 40f) / 2
            when (slot.state) {
                SlotState.TAKEN -> disc(Page.LEFT, mark, 40f, Ink.BLACK)
                SlotState.SKIPPED -> disc(Page.LEFT, mark, 40f, Ink.DARK)
                else -> ring(Page.LEFT, mark, 40f, 5f, Ink.BLACK)
            }
            text(rowName, "${med.schedule.name} ${med.schedule.dose}", Page.LEFT + 70f, top)
            val word = when (slot.state) {
                SlotState.TAKEN -> "taken"
                SlotState.SKIPPED -> "skipped"
                SlotState.MISSED -> "missed"
                SlotState.DUE -> "due"
                SlotState.UPCOMING -> "later"
            }
            text(
                rowRight, "${Formats.clock(slot.slot.scheduledFor.toLocalTime())} · $word",
                Page.RIGHT, top + (rowName.lineBox - rowRight.lineBox) / 2, Align.RIGHT,
            )
        }
        if (today.size > rowTops.size) {
            text(rowName.with(Ink.DARK), "+${today.size - shown.size} more on the phone", Page.LEFT + 70f, rowTops.last())
        }
    }

    companion object {
        const val MAX_TODAY_ROWS = 5

        /** The busiest day of the month decides the row count, so every day fits the same frame. */
        fun todayRowCount(cfg: RenderConfig): Int {
            val busiest = (1..cfg.month.lengthOfMonth()).maxOfOrNull { d ->
                cfg.schedules.sumOf { it.slotsOn(cfg.month.atDay(d)).size }
            } ?: 0
            return busiest.coerceIn(1, MAX_TODAY_ROWS)
        }
    }
}

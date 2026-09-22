package io.github.couchknight.healthnote.render

import io.github.couchknight.healthnote.model.Formats
import io.github.couchknight.healthnote.model.MonthReport
import io.github.couchknight.healthnote.model.SlotState
import kotlin.math.floor
import kotlin.math.min

/** One marker per medication per day: the day's slots folded into a single state. */
internal fun dayState(states: List<SlotState>): SlotState? = when {
    states.isEmpty() -> null
    SlotState.MISSED in states -> SlotState.MISSED
    states.any { it == SlotState.DUE || it == SlotState.UPCOMING } -> SlotState.UPCOMING
    states.all { it == SlotState.SKIPPED } -> SlotState.SKIPPED
    else -> SlotState.TAKEN
}

/**
 * Page 2 as a calendar grid (design 1b), for up to four schedules: 7 columns, Monday first,
 * one marker per medication in each day cell, and a per-medication rail on the right.
 */
internal class MedicationGridPage(private val cfg: RenderConfig) {
    val frame = Layer()
    val links = mutableListOf<Link>()

    private val cellW: Float
    private val cellH: Float
    private val gridTop: Float
    private val leading: Int
    private val railX: Float
    private val railW: Float
    private val blockTops: List<Float>
    private val detailLines: Int
    private val blockPad: Float
    private val totalValueTop: Float
    private val totalSubTop: Float

    private val pct = Style(Face.CASLON_BOLD, 62f)
    private val detail = Style(Face.FRANKLIN_LIGHT, 30f, Ink.DARK, lineHeight = 1.35f)
    private val totalValue = Style(Face.CASLON_BOLD, 78f)

    init {
        val top = Chrome.header(frame, "Medication", Formats.monthTitle(cfg.month))

        // Legend. The order note on the right wraps if the names are long.
        val legendTop = top + 34f
        val legendText = Styles.caption
        val items = listOf("taken", "missed", "future", "skipped")
        val itemsW = items.sumOf { (26f + 12f + legendText.width(it)).toDouble() }.toFloat() + 26f * (items.size - 1)
        val orderNote = cfg.schedules.joinToString(" · ") { it.name } + ", in that order per cell"
        val noteW = Page.CONTENT_W - itemsW - 26f
        val noteLines = lineCount(legendText, orderNote, noteW)
        val legendH = maxOf(30f, noteLines * 30f)
        var x = Page.LEFT
        items.forEachIndexed { i, label ->
            val my = legendTop + (legendH - 26f) / 2
            when (i) {
                0 -> frame.disc(x, my, 26f, Ink.BLACK)
                1 -> frame.ring(x, my, 26f, 4f, Ink.BLACK)
                2 -> frame.ring(x, my, 26f, 3f, Ink.MID)
                else -> frame.disc(x, my, 26f, Ink.DARK)
            }
            x += 26f + 12f
            x += frame.text(legendText, label, x, legendTop + (legendH - 30f) / 2) + 26f
        }
        frame.paragraph(legendText, orderNote, Page.RIGHT - noteW, legendTop, noteW, align = Align.RIGHT)

        // Grid and rail share one row: grid flexes, rail is 300 wide, 30 apart.
        val mainTop = legendTop + legendH + 30f
        railW = 300f
        val gridW = Page.CONTENT_W - 30f - railW
        cellW = gridW / 7
        val head = Style(Face.FRANKLIN_SEMIBOLD, 30f, tracking = .08f)
        listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { i, d ->
            frame.text(head.with(if (i >= 5) Ink.DARK else Ink.BLACK), d, Page.LEFT + i * cellW + 8f, mainTop)
        }
        frame.hrule(Page.LEFT, mainTop + 46f, gridW, 3f, Ink.BLACK)
        gridTop = mainTop + 49f

        leading = cfg.month.atDay(1).dayOfWeek.value - 1
        val rows = (leading + cfg.month.lengthOfMonth() + 6) / 7
        // 250-unit cells as designed; six-row months shrink to stay clear of the footer.
        cellH = min(250f, floor((Page.FOOTER_TOP - 48f - gridTop) / rows))
        val dayNum = Style(Face.FRANKLIN_REGULAR, 38f)
        for (i in 0 until rows * 7) {
            val cx = Page.LEFT + (i % 7) * cellW
            val cy = gridTop + (i / 7) * cellH
            frame.vrule(cx + cellW - Page.HAIRLINE, cy, cellH)
            frame.hrule(cx, cy + cellH - Page.HAIRLINE, cellW)
            val day = i - leading + 1
            if (day in 1..cfg.month.lengthOfMonth()) {
                frame.text(dayNum.with(if (i % 7 >= 5) Ink.DARK else Ink.BLACK), day.toString(), cx + 7f, cy + 12f)
            }
        }
        val gridBottom = gridTop + rows * cellH

        // Rail: 3-unit rule on its left, content 26 in.
        val railLeft = Page.LEFT + gridW + 30f
        frame.vrule(railLeft, mainTop, gridBottom - mainTop, 3f, Ink.BLACK)
        railX = railLeft + 3f + 26f
        val contentW = railW - 3f - 26f
        frame.text(Styles.label, "Per med", railX, mainTop)

        // Month-to-date block pinned to the rail's bottom.
        val totalSubLines = 3
        val totalH = 3f + 26f + 30f + 10f + totalValue.size + 10f + totalSubLines * detail.lineBox
        val totalTop = gridBottom - totalH
        frame.hrule(railX, totalTop, contentW, 3f, Ink.BLACK)
        // "Month to date" in the mockup overflowed the 271-unit rail at this size; HTML wrapped it.
        frame.text(Styles.label.copy(tracking = .1f), "To date", railX, totalTop + 29f)
        totalValueTop = totalTop + 29f + 30f + 10f
        totalSubTop = totalValueTop + totalValue.size + 10f

        // Per-medication blocks, padding squeezed if four don't fit above the total.
        detailLines = if (cfg.schedules.size <= 3) 3 else 2
        val blocksTop = mainTop + 30f + 22f
        val content = Page.HAIRLINE + 38f + 10f + pct.size + 10f + detailLines * detail.lineBox
        val room = (totalTop - 20f - blocksTop) / maxOf(1, cfg.schedules.size)
        blockPad = ((room - content) / 2).coerceIn(8f, 26f)
        val pitch = content + 2 * blockPad
        val name = Style(Face.FRANKLIN_REGULAR, 38f)
        blockTops = cfg.schedules.mapIndexed { i, s ->
            val y = blocksTop + i * pitch
            frame.hrule(railX, y, contentW)
            val rowTop = y + Page.HAIRLINE + blockPad
            frame.disc(railX, rowTop + 6f, 26f, if (i == 0) Ink.BLACK else Ink.DARK)
            frame.text(name, s.name, railX + 40f, rowTop)
            rowTop
        }

        Chrome.footer(frame, links, Section.MEDS)
    }

    fun paint(r: MonthReport): Layer = Layer().apply {
        val n = cfg.schedules.size
        val size = min(28f, (cellW - 14f - Page.HAIRLINE - 6f * (n - 1)) / maxOf(1, n))
        for (day in 1..cfg.month.lengthOfMonth()) {
            val i = leading + day - 1
            val cx = Page.LEFT + (i % 7) * cellW
            val cy = gridTop + (i / 7) * cellH
            val markTop = cy + cellH - Page.HAIRLINE - 12f - size
            cfg.schedules.forEachIndexed { k, s ->
                val state = dayState(r.state(s, day)) ?: return@forEachIndexed
                val mx = cx + 7f + k * (size + 6f)
                when (state) {
                    SlotState.TAKEN -> disc(mx, markTop, size, Ink.BLACK)
                    SlotState.MISSED -> ring(mx, markTop, size, 5f, Ink.BLACK)
                    SlotState.SKIPPED -> disc(mx, markTop, size, Ink.DARK)
                    SlotState.DUE, SlotState.UPCOMING -> ring(mx, markTop, size, 3f, Ink.MID)
                }
            }
        }

        val contentW = railW - 3f - 26f
        cfg.schedules.forEachIndexed { i, s ->
            val med = r.meds.firstOrNull { it.schedule.id == s.id } ?: return@forEachIndexed
            val pctTop = blockTops[i] + 38f + 10f
            text(pct, med.counts.percent?.let { "$it%" } ?: "—", railX, pctTop)
            val c = med.counts
            val lines = buildList {
                add("${c.taken} of ${c.taken + c.missed} · streak ${med.streakDays}")
                if (c.due > 0) add("due today")
                med.lastLapse?.let {
                    val verb = if (it.state == SlotState.SKIPPED) "skipped" else "missed"
                    add("$verb ${Formats.dayLabel(it.slot.date)}")
                } ?: add("nothing missed")
            }
            paragraph(detail, lines.joinToString("\n"), railX, pctTop + pct.size + 10f, contentW, detailLines)
        }

        val o = r.overall
        text(totalValue, o.percent?.let { "$it%" } ?: "—", railX, totalValueTop)
        // One phrase per line: the rail is only 271 units wide at 30-unit text.
        val lines = listOfNotNull(
            "${o.taken} taken · ${o.missed} missed",
            if (o.skipped > 0) "${o.skipped} skipped" else null,
            "${o.remaining} still ahead",
        )
        paragraph(detail, lines.joinToString("\n"), railX, totalSubTop, contentW, 3)
    }
}

/**
 * Page 2 as PerMedStrip (design 1c), chosen automatically above four schedules: one row per
 * medication across 31 fixed day columns.
 */
internal class MedicationStripPage(private val cfg: RenderConfig) {
    val frame = Layer()
    val links = mutableListOf<Link>()

    private val cellsTops: List<Float>
    private val headerTops: List<Float>
    private val cellW: Float
    private val cellH: Float

    private val pct = Style(Face.CASLON_BOLD, 48f)

    init {
        val top = Chrome.header(
            frame, "Medication", "${Formats.monthTitle(cfg.month)} · ${cfg.schedules.size} schedules",
        )
        val first = Chrome.caption(
            frame,
            "One row per medication, 31 fixed columns. Selected automatically above four schedules, " +
                "because cell markers stop being legible.",
            top, 26f, 40f,
        )
        val gap = 6f
        cellW = (Page.CONTENT_W - gap * 30) / 31
        val natural = Page.HAIRLINE + 30f + pct.size + 20f + 54f + 34f
        val ticksH = 4f + 30f
        val room = Page.FOOTER_TOP - 40f - ticksH - first
        val k = min(1f, room / (cfg.schedules.size * natural))
        cellH = 54f * k
        val pitch = natural * k

        val name = Style(Face.FRANKLIN_REGULAR, 44f)
        val sched = Style(Face.FRANKLIN_LIGHT, 34f, Ink.DARK)
        val heads = mutableListOf<Float>()
        val cells = mutableListOf<Float>()
        cfg.schedules.forEachIndexed { i, s ->
            val y = first + i * pitch
            frame.hrule(Page.LEFT, y, Page.CONTENT_W)
            val rowTop = y + Page.HAIRLINE + 30f * k
            // Baseline-aligned row: name, schedule, then the painted percentage in a 150 box.
            val baseline = pct.baseline(rowTop)
            val nameTop = rowTop + (baseline - name.baseline(rowTop))
            val schedTop = rowTop + (baseline - sched.baseline(rowTop))
            frame.text(name, "${s.name} ${s.dose}", Page.LEFT, nameTop)
            val days = if (s.daysOfWeek.size == 7) "daily" else Formats.days(s.daysOfWeek)
            frame.text(sched, "${Formats.times(s.timesOfDay)} $days", Page.RIGHT - 150f - 20f, schedTop, Align.RIGHT)
            heads += rowTop
            cells += rowTop + pct.size + 20f * k
        }
        headerTops = heads
        cellsTops = cells

        val ticksTop = first + cfg.schedules.size * pitch + 4f
        val days = cfg.month.lengthOfMonth()
        for (d in listOf(1, 10, 20, days)) {
            frame.text(Styles.caption, d.toString(), Page.LEFT + (d - 1) * (cellW + gap), ticksTop, Align.CENTER, cellW)
        }

        Chrome.footer(frame, links, Section.MEDS)
    }

    fun paint(r: MonthReport): Layer = Layer().apply {
        val gap = 6f
        cfg.schedules.forEachIndexed { i, s ->
            val med = r.meds.firstOrNull { it.schedule.id == s.id } ?: return@forEachIndexed
            text(pct, med.counts.percent?.let { "$it%" } ?: "—", Page.RIGHT, headerTops[i], Align.RIGHT)
            for (d in 1..cfg.month.lengthOfMonth()) {
                val state = dayState(r.state(s, d)) ?: continue
                val x = Page.LEFT + (d - 1) * (cellW + gap)
                val y = cellsTops[i]
                when (state) {
                    SlotState.TAKEN -> rect(x, y, cellW, cellH, Ink.BLACK)
                    SlotState.SKIPPED -> rect(x, y, cellW, cellH, Ink.DARK)
                    SlotState.MISSED -> border(x, y, cellW, cellH, 4f, Ink.BLACK)
                    SlotState.DUE, SlotState.UPCOMING -> border(x, y, cellW, cellH, Page.HAIRLINE, Ink.MID)
                }
            }
        }
    }
}

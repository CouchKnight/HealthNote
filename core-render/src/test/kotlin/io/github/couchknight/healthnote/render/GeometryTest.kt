package io.github.couchknight.healthnote.render

import io.github.couchknight.healthnote.model.DoseEvent
import io.github.couchknight.healthnote.model.DoseSource
import io.github.couchknight.healthnote.model.DoseStatus
import io.github.couchknight.healthnote.model.MedicationSchedule
import io.github.couchknight.healthnote.model.MonthReport
import io.github.couchknight.healthnote.model.sample.SampleData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

/**
 * The regression guard for ink alignment (DESIGN.md §1.3, §6): within a month, the frame and
 * link rects are identical whatever the data, and only the painted layer changes.
 */
class GeometryTest {
    private val month = SampleData.month
    private val config = RenderConfig(month, MonthReport.orderSchedules(SampleData.schedules))

    private fun report(now: LocalDateTime, withData: Boolean = true) = MonthReport(
        month, now, SampleData.schedules,
        if (withData) SampleData.events.filter { it.loggedAt <= now } else emptyList(),
        if (withData) SampleData.nights.filterKeys { !it.isAfter(now.toLocalDate()) } else emptyMap(),
    )

    private val empty = report(LocalDateTime.of(2026, 9, 1, 6, 0), withData = false)
    private val partial = report(LocalDateTime.of(2026, 9, 10, 21, 0))
    private val full = report(SampleData.now)
    private val finished = MonthReport(
        month, LocalDateTime.of(2026, 10, 1, 6, 0), SampleData.schedules,
        SampleData.schedules.flatMap { s ->
            (1..30).flatMap { s.slotsOn(month.atDay(it)) }.map {
                DoseEvent("${s.id}${it.scheduledFor}", s.id, it.scheduledFor, it.scheduledFor, DoseStatus.TAKEN, DoseSource.WIDGET)
            }
        },
        SampleData.nights,
    )

    @Test
    fun `frame and links are identical for empty, partial, full and finished months`() {
        val docs = listOf(empty, partial, full, finished).map { DocumentRenderer.render(it, config) }
        val reference = docs.first()
        for (doc in docs.drop(1)) {
            assertEquals(reference.pages.size, doc.pages.size)
            reference.pages.zip(doc.pages).forEachIndexed { i, (a, b) ->
                assertEquals(a.frame, b.frame, "frame of page ${i + 1} moved with the data")
                assertEquals(a.links, b.links, "links of page ${i + 1} moved with the data")
            }
        }
        // ...and the painted layer really does carry the data.
        for (page in 0..3) {
            assertNotEquals(docs[1].pages[page].paint, docs[2].pages[page].paint, "page ${page + 1} paint is static")
        }
    }

    @Test
    fun `the notes page is entirely static`() {
        val a = DocumentRenderer.render(empty, config).pages[4]
        val b = DocumentRenderer.render(full, config).pages[4]
        assertEquals(a, b)
        assertTrue(a.paint.isEmpty())
    }

    @Test
    fun `every page has the five footer links and the hub links each section`() {
        val doc = DocumentRenderer.render(full, config)
        assertEquals(listOf("Hub", "Medication", "Sleep", "Sleep detail", "Notes"), doc.pages.map { it.title })
        for (page in doc.pages) {
            val footer = page.links.filter { it.rect.y >= Page.FOOTER_TOP }
            assertEquals(listOf(0, 1, 2, 3, 4), footer.map { it.target })
            assertEquals(Page.CONTENT_W, footer.sumOf { it.rect.w.toDouble() }.toFloat(), 0.01f)
        }
        assertEquals(listOf(1, 2, 3, 4), doc.pages[0].links.filter { it.rect.y < Page.FOOTER_TOP }.map { it.target })
    }

    @Test
    fun `above four schedules the strip layout is chosen`() {
        assertTrue(!config.usesStrip)
        assertTrue(RenderConfig(month, schedules(5)).usesStrip)
        assertTrue(!RenderConfig(month, schedules(4)).usesStrip)
    }

    @ParameterizedTest(name = "{0}, {1} schedules")
    @MethodSource("layouts")
    fun `everything stays on the page and clear of the footer`(month: YearMonth, count: Int) {
        val cfg = RenderConfig(month, schedules(count))
        val now = month.atDay(month.lengthOfMonth()).atTime(23, 0)
        val events = cfg.schedules.flatMap { s ->
            (1..month.lengthOfMonth()).flatMap { s.slotsOn(month.atDay(it)) }.mapIndexedNotNull { i, slot ->
                if (i % 9 == 4) null else DoseEvent(
                    "${s.id}$i", s.id, slot.scheduledFor, slot.scheduledFor,
                    if (i % 11 == 3) DoseStatus.SKIPPED else DoseStatus.TAKEN, DoseSource.MANUAL,
                )
            }
        }
        val nights = SampleData.nights.values.associate { n ->
            val d = month.atDay(minOf(n.night.dayOfMonth, month.lengthOfMonth()))
            d to n.copy(night = d)
        }
        val doc = DocumentRenderer.render(MonthReport(month, now, cfg.schedules, events, nights), cfg)
        for ((p, page) in doc.pages.withIndex()) {
            for (op in page.frame + page.paint) {
                val b = bounds(op)
                val where = "page ${p + 1}: $op"
                assertTrue(b.x >= -0.5f && b.y >= -0.5f && b.x + b.w <= Page.W + 0.5f && b.y + b.h <= Page.H + 0.5f, where)
                val inFooter = b.y >= Page.FOOTER_TOP - 0.5f
                assertTrue(inFooter || b.y + b.h <= Page.FOOTER_TOP - 10f, "overlaps footer: $where")
                if (!inFooter && op is Op.Text) {
                    assertTrue(b.x >= Page.LEFT - 0.5f && b.x + b.w <= Page.RIGHT + 0.5f, "outside margins: $where")
                }
            }
        }
    }

    @Test
    fun `strokes never go under two units and only the five inks appear`() {
        for (cfg in listOf(config, RenderConfig(month, schedules(6)))) {
            val doc = DocumentRenderer.render(full.let { MonthReport(month, it.now, cfg.schedules, it.events, it.nights) }, cfg)
            for (op in doc.pages.flatMap { it.frame + it.paint }) {
                when (op) {
                    is Op.FillRect -> assertTrue(minOf(op.w, op.h) >= 2f, "thin rect $op")
                    is Op.Line -> assertTrue(op.width >= 2f, "thin line $op")
                    is Op.Polyline -> assertTrue(op.width >= 2f, "thin polyline $op")
                    is Op.Circle -> assertTrue(op.strokeWidth == 0f || op.strokeWidth >= 2f, "thin ring $op")
                    is Op.StrokeRect -> assertTrue(op.width >= 2f, "thin border $op")
                    is Op.Text -> Unit
                }
            }
        }
    }

    @Test
    fun `every character drawn exists in its font`() {
        val docs = listOf(empty, partial, full, finished).map { DocumentRenderer.render(it, config) } +
            DocumentRenderer.render(MonthReport(month, SampleData.now, schedules(6), emptyList(), emptyMap()), RenderConfig(month, schedules(6)))
        for (op in docs.flatMap { d -> d.pages.flatMap { it.frame + it.paint } }.filterIsInstance<Op.Text>()) {
            val m = FontMetrics.of(op.face)
            op.text.codePoints().forEach { cp ->
                assertTrue(m.hasGlyph(cp), "${op.face} has no glyph for U+${Integer.toHexString(cp)} in \"${op.text}\"")
            }
        }
    }

    private fun bounds(op: Op): Rect = when (op) {
        is Op.FillRect -> Rect(op.x, op.y, op.w, op.h)
        is Op.StrokeRect -> Rect(op.x - op.width / 2, op.y - op.width / 2, op.w + op.width, op.h + op.width)
        is Op.Line -> Rect(minOf(op.x1, op.x2), minOf(op.y1, op.y2), kotlin.math.abs(op.x2 - op.x1), kotlin.math.abs(op.y2 - op.y1))
        is Op.Polyline -> {
            val xs = op.points.map { it.x }; val ys = op.points.map { it.y }
            Rect(xs.min(), ys.min(), xs.max() - xs.min(), ys.max() - ys.min())
        }
        is Op.Circle -> {
            val r = op.r + op.strokeWidth / 2
            Rect(op.cx - r, op.cy - r, 2 * r, 2 * r)
        }
        is Op.Text -> {
            val m = FontMetrics.of(op.face)
            val w = m.width(op.text, op.size, op.letterSpacing) - op.letterSpacing
            Rect(op.x, op.baseline - m.ascent * op.size, w, (m.ascent + m.descent) * op.size)
        }
    }

    companion object {
        private val NAMES = listOf("Vyvanse", "Omega 3", "Vitamin C", "Magnesium", "Vitamin D", "Melatonin")

        fun schedules(count: Int): List<MedicationSchedule> = MonthReport.orderSchedules(
            (0 until count).map { i ->
                MedicationSchedule(
                    "m$i", NAMES[i], "${(i + 1) * 100} mg", listOf(LocalTime.of(7 + i, 0)),
                    if (i % 2 == 0) DayOfWeek.entries.toSet() else DayOfWeek.entries.take(5).toSet(),
                    LocalDate.of(2026, 1, 1), colorSlot = i,
                )
            },
        )

        @JvmStatic
        fun layouts() = (1..12).flatMap { m -> (1..6).map { n -> arrayOf<Any>(YearMonth.of(2026, m), n) } }
    }
}

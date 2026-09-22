package io.github.couchknight.healthnote.render

import io.github.couchknight.healthnote.model.MedicationSchedule
import io.github.couchknight.healthnote.model.MonthReport
import io.github.couchknight.healthnote.model.SleepTarget
import java.time.YearMonth

/**
 * DESIGN.md §4.1. Both devices are 3:4, so one PDF serves both; the profile may only change
 * content density, never the page box. Only the Nomad layout is designed so far.
 */
enum class LayoutProfile { NOMAD, MANTA }

/**
 * Everything the page frame may depend on. Layout geometry is a pure function of this
 * (DESIGN.md §1.3), never of logged doses or sleep.
 *
 * The schedule list is part of the config because it decides the grid-vs-strip layout and the
 * row labels. Changing schedules mid-month therefore changes the frame; the app should apply
 * schedule edits to the document from the next month, or warn that ink may shift.
 */
data class RenderConfig(
    val month: YearMonth,
    val schedules: List<MedicationSchedule>,
    val target: SleepTarget = SleepTarget(),
    val profile: LayoutProfile = LayoutProfile.NOMAD,
) {
    /** Above four schedules the calendar cells cannot hold legible markers (DESIGN.md §4.2). */
    val usesStrip: Boolean get() = schedules.size > 4

    companion object {
        fun from(report: MonthReport, profile: LayoutProfile = LayoutProfile.NOMAD) =
            RenderConfig(report.month, report.schedules, report.target, profile)
    }
}

/** Page order, footer labels and outline titles. */
enum class Section(val nav: String, val outline: String) {
    HUB("Hub", "Hub"),
    MEDS("Meds", "Medication"),
    SLEEP("Sleep", "Sleep"),
    DETAIL("Detail", "Sleep detail"),
    NOTES("Notes", "Notes"),
}

internal object Chrome {
    /** Title left, label right, bottoms aligned, 4-unit rule under. Returns the y below the rule. */
    fun header(
        frame: Layer,
        title: String,
        right: String,
        titleSize: Float = 80f,
        rightStyle: Style = Styles.headerRight,
    ): Float {
        frame.text(Styles.headerTitle.copy(size = titleSize), title, Page.LEFT, Page.PAD_TOP)
        val bottom = Page.PAD_TOP + titleSize
        frame.text(rightStyle, right, Page.RIGHT, bottom - rightStyle.lineBox, Align.RIGHT)
        frame.hrule(Page.LEFT, bottom + 28f, Page.CONTENT_W, 4f, Ink.BLACK)
        return bottom + 28f + 4f
    }

    /** The one-paragraph explainer under a header. Returns the y below its bottom padding. */
    fun caption(frame: Layer, text: String, top: Float, padTop: Float, padBottom: Float): Float {
        val lines = frame.paragraph(Styles.caption, text, Page.LEFT, top + padTop, Page.CONTENT_W)
        return top + padTop + lines * Styles.caption.lineBox + padBottom
    }

    /** Persistent footer nav (DESIGN.md §4.2): five `/GoTo` rects, the current page shaded. */
    fun footer(frame: Layer, links: MutableList<Link>, active: Section) {
        frame.hrule(Page.LEFT, Page.FOOTER_TOP, Page.CONTENT_W, 3f, Ink.BLACK)
        val top = Page.FOOTER_TOP + 3f
        val sections = Section.entries
        val sep = Page.HAIRLINE
        val itemW = (Page.CONTENT_W - sep * (sections.size - 1)) / sections.size
        var x = Page.LEFT
        for ((i, s) in sections.withIndex()) {
            val start = x
            if (i > 0) {
                frame.vrule(x, top, Page.H - top)
                x += sep
            }
            if (s == active) frame.rect(x, top, itemW, Page.H - top, Ink.MID)
            frame.text(
                Styles.nav.with(if (s == active) Ink.BLACK else Ink.DARK),
                s.nav, x, top + 30f, Align.CENTER, itemW,
            )
            x += itemW
            links += Link(Rect(start, Page.FOOTER_TOP, x - start, Page.H - Page.FOOTER_TOP), s.ordinal)
        }
    }
}

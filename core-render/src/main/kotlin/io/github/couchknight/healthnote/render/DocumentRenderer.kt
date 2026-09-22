package io.github.couchknight.healthnote.render

import io.github.couchknight.healthnote.model.Formats
import io.github.couchknight.healthnote.model.MonthReport

/**
 * Builds the five-page month document (DESIGN.md §4.2) as display lists. The PDF backend in
 * `:core-render-pdf` / `:core-render-android` turns this into bytes.
 */
object DocumentRenderer {
    fun render(report: MonthReport, config: RenderConfig = RenderConfig.from(report)): DocumentDrawing {
        require(report.month == config.month) { "report is for ${report.month}, config for ${config.month}" }
        val hub = HubPage(config)
        val pages = buildList {
            add(PageDrawing(Section.HUB.outline, hub.frame.ops, hub.paint(report).ops, hub.links))
            if (config.usesStrip) {
                val p = MedicationStripPage(config)
                add(PageDrawing(Section.MEDS.outline, p.frame.ops, p.paint(report).ops, p.links))
            } else {
                val p = MedicationGridPage(config)
                add(PageDrawing(Section.MEDS.outline, p.frame.ops, p.paint(report).ops, p.links))
            }
            val sleep = SleepChartPage(config)
            add(PageDrawing(Section.SLEEP.outline, sleep.frame.ops, sleep.paint(report).ops, sleep.links))
            val detail = SleepDetailPage(config)
            add(PageDrawing(Section.DETAIL.outline, detail.frame.ops, detail.paint(report).ops, detail.links))
            val notes = NotesPage(config)
            add(PageDrawing(Section.NOTES.outline, notes.frame.ops, emptyList(), notes.links))
        }
        return DocumentDrawing(
            fileName = Formats.documentFileName(config.month),
            title = "HealthNote · ${Formats.monthTitle(config.month)}",
            pages = pages,
        )
    }
}

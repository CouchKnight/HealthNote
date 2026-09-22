package io.github.couchknight.healthnote.render.pdf

import io.github.couchknight.healthnote.model.MonthReport
import io.github.couchknight.healthnote.model.sample.SampleData
import io.github.couchknight.healthnote.render.DocumentRenderer
import java.io.File

/** `./gradlew :core-render-pdf:renderSample` writes the design's sample month as a real PDF. */
fun main(args: Array<String>) {
    val dir = File(args.firstOrNull() ?: "build/sample").apply { mkdirs() }
    val report = MonthReport(SampleData.month, SampleData.now, SampleData.schedules, SampleData.events, SampleData.nights)
    val drawing = DocumentRenderer.render(report)
    val out = File(dir, drawing.fileName)
    out.writeBytes(PdfWriter.toBytes(drawing))
    println("Wrote ${out.absolutePath}")
}

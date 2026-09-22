package io.github.couchknight.healthnote.render.pdf

import io.github.couchknight.healthnote.model.MonthReport
import io.github.couchknight.healthnote.model.sample.SampleData
import io.github.couchknight.healthnote.render.DocumentRenderer
import io.github.couchknight.healthnote.render.Page
import io.github.couchknight.healthnote.render.RenderConfig
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitWidthDestination
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import javax.imageio.ImageIO

/** DESIGN.md §6 "PDF structure test": parse the output back and check navigation. */
class PdfStructureTest {
    private val report = MonthReport(
        SampleData.month, SampleData.now, SampleData.schedules, SampleData.events, SampleData.nights,
    )
    private val drawing = DocumentRenderer.render(report)
    private val bytes = PdfWriter.toBytes(drawing)

    @Test
    fun `five pages at the Nomad's native size`() {
        PDDocument.load(bytes).use { pd ->
            assertEquals(5, pd.numberOfPages)
            for (page in pd.pages) {
                assertEquals(Page.W, page.mediaBox.width)
                assertEquals(Page.H, page.mediaBox.height)
            }
        }
    }

    @Test
    fun `every nav rect is a borderless GoTo to the intended page`() {
        PDDocument.load(bytes).use { pd ->
            pd.pages.forEachIndexed { i, page ->
                val links = page.annotations.filterIsInstance<PDAnnotationLink>()
                assertEquals(drawing.pages[i].links.size, links.size, "page ${i + 1}")
                links.zip(drawing.pages[i].links).forEach { (annotation, expected) ->
                    val dest = (annotation.action as PDActionGoTo).destination as PDPageFitWidthDestination
                    assertEquals(expected.target, pd.pages.indexOf(dest.page), "page ${i + 1} link")
                    assertEquals(0f, annotation.borderStyle.width)
                    val r = annotation.rectangle
                    assertEquals(expected.rect.x, r.lowerLeftX, 0.01f)
                    assertEquals(Page.H - expected.rect.y - expected.rect.h, r.lowerLeftY, 0.01f)
                }
                val footer = links.filter { it.rectangle.upperRightY <= Page.FOOTER_H + 0.01f }
                assertEquals(5, footer.size, "footer nav on page ${i + 1}")
            }
        }
    }

    @Test
    fun `the outline lists all five pages in order`() {
        PDDocument.load(bytes).use { pd ->
            val items = generateSequence(pd.documentCatalog.documentOutline.firstChild) { it.nextSibling }.toList()
            assertEquals(listOf("Hub", "Medication", "Sleep", "Sleep detail", "Notes"), items.map { it.title })
            items.forEachIndexed { i, item -> assertEquals(i, pd.pages.indexOf(item.findDestinationPage(pd))) }
        }
    }

    @Test
    fun `the same data renders to the same bytes`() {
        assertArrayEquals(bytes, PdfWriter.toBytes(DocumentRenderer.render(report)))
        PDDocument.load(bytes).use { pd ->
            assertTrue(pd.document.trailer.getCOSArray(COSName.ID) != null)
            assertEquals(null, pd.documentInformation.creationDate)
        }
    }

    @Test
    fun `text is real text with the fonts embedded`() {
        PDDocument.load(bytes).use { pd ->
            val hub = PDFTextStripper().apply { startPage = 1; endPage = 1 }.getText(pd)
            assertTrue("September 2026" in hub, hub)
            assertTrue("94%" in hub, hub)
            assertTrue("Omega 3 1000 mg" in hub, hub)
            for (page in pd.pages) {
                for (name in page.resources.fontNames) {
                    assertTrue(page.resources.getFont(name).isEmbedded)
                }
            }
        }
    }

    @Test
    fun `render previews for review`() {
        val dir = File(System.getProperty("healthnote.previewDir") ?: return).apply { mkdirs() }
        File(dir, drawing.fileName).writeBytes(bytes)
        val strip = DocumentRenderer.render(
            MonthReport(SampleData.month, SampleData.now, SampleData.schedules + extras(), SampleData.events, SampleData.nights),
        )
        val stripBytes = PdfWriter.toBytes(strip)
        PDDocument.load(bytes).use { pd ->
            val r = PDFRenderer(pd)
            for (i in 0 until pd.numberOfPages) ImageIO.write(r.renderImage(i, 0.5f), "png", File(dir, "page-${i + 1}.png"))
        }
        PDDocument.load(stripBytes).use { pd ->
            ImageIO.write(PDFRenderer(pd).renderImage(1, 0.5f), "png", File(dir, "page-2-strip.png"))
        }
        assertEquals(RenderConfig.from(report).month, SampleData.month)
    }

    private fun extras() = listOf(
        Triple("mg", "Magnesium", "400 mg") to 22,
        Triple("vd", "Vitamin D", "2000 IU") to 8,
        Triple("mel", "Melatonin", "2 mg") to 22,
    ).mapIndexed { i, (t, hour) ->
        SampleData.schedules[0].copy(id = t.first, name = t.second, dose = t.third,
            timesOfDay = listOf(java.time.LocalTime.of(hour, if (i == 2) 30 else 0)), colorSlot = 3 + i)
    }
}

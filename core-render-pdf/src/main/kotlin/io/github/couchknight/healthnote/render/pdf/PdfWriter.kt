package io.github.couchknight.healthnote.render.pdf

import io.github.couchknight.healthnote.render.DocumentDrawing
import io.github.couchknight.healthnote.render.Face
import io.github.couchknight.healthnote.render.Link
import io.github.couchknight.healthnote.render.Op
import io.github.couchknight.healthnote.render.Page
import org.apache.pdfbox.cos.COSArray
import org.apache.pdfbox.cos.COSInteger
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.cos.COSString
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import org.apache.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitWidthDestination
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.security.MessageDigest

/**
 * Writes a [DocumentDrawing] as PDF (DESIGN.md §4.3): 1404 x 1872 MediaBox, DeviceGray only,
 * fonts embedded as subsets, internal `/GoTo` link annotations with explicit fit-width page
 * destinations and zero-width borders, and one `/Outlines` entry per page.
 *
 * Output is deterministic: the same drawing always produces the same bytes (no creation date,
 * a document ID derived from the file name), so the publish worker can skip an upload when the
 * hash has not changed (DESIGN.md §5.3).
 *
 * This file is compiled twice: here against Apache PDFBox, and in `:core-render-android`
 * against pdfbox-android with `org.apache.pdfbox` rewritten to `com.tom_roush.pdfbox`. Use only
 * API present in both (they share the 2.0.27 surface) and nothing from java.awt.
 */
object PdfWriter {
    fun toBytes(doc: DocumentDrawing): ByteArray = ByteArrayOutputStream().also { write(doc, it) }.toByteArray()

    fun write(doc: DocumentDrawing, out: OutputStream) {
        PDDocument().use { pd ->
            val fonts = HashMap<Face, PDType0Font>()
            fun font(face: Face) = fonts.getOrPut(face) { face.open().use { PDType0Font.load(pd, it, true) } }

            val pages = doc.pages.map { PDPage(PDRectangle(Page.W, Page.H)).also(pd::addPage) }
            doc.pages.forEachIndexed { i, drawing ->
                PDPageContentStream(pd, pages[i]).use { cs ->
                    for (op in drawing.frame) draw(cs, op, ::font)
                    for (op in drawing.paint) draw(cs, op, ::font)
                }
                pages[i].setAnnotations(drawing.links.map { link(it, pages) })
            }

            val outline = PDDocumentOutline()
            doc.pages.forEachIndexed { i, drawing ->
                val item = PDOutlineItem()
                item.setTitle(drawing.title)
                item.setDestination(fitWidth(pages[i]))
                outline.addLast(item)
            }
            pd.getDocumentCatalog().setDocumentOutline(outline)

            val info = pd.getDocumentInformation()
            info.setTitle(doc.title)
            info.setProducer("HealthNote")

            val id = MessageDigest.getInstance("SHA-256").digest(doc.fileName.toByteArray()).copyOf(16)
            val ids = COSArray()
            ids.add(COSString(id))
            ids.add(COSString(id))
            pd.getDocument().getTrailer().setItem(COSName.ID, ids)

            pd.save(out)
        }
    }

    private fun fitWidth(page: PDPage): PDPageFitWidthDestination {
        val d = PDPageFitWidthDestination()
        d.setPage(page)
        d.setTop(Page.H.toInt())
        return d
    }

    private fun link(l: Link, pages: List<PDPage>): PDAnnotation {
        val a = PDAnnotationLink()
        a.setRectangle(PDRectangle(l.rect.x, Page.H - l.rect.y - l.rect.h, l.rect.w, l.rect.h))
        val bs = PDBorderStyleDictionary()
        bs.setWidth(0f)
        a.setBorderStyle(bs)
        // Also the legacy /Border array, for readers that ignore /BS.
        val border = COSArray()
        repeat(3) { border.add(COSInteger.ZERO) }
        a.getCOSObject().setItem(COSName.BORDER, border)
        val action = PDActionGoTo()
        action.setDestination(fitWidth(pages[l.target]))
        a.setAction(action)
        return a
    }

    private fun y(top: Float) = Page.H - top

    private fun draw(cs: PDPageContentStream, op: Op, font: (Face) -> PDType0Font) {
        when (op) {
            is Op.FillRect -> {
                cs.setNonStrokingColor(op.ink.gray)
                cs.addRect(op.x, y(op.y + op.h), op.w, op.h)
                cs.fill()
            }
            is Op.StrokeRect -> {
                cs.setStrokingColor(op.ink.gray)
                cs.setLineWidth(op.width)
                cs.addRect(op.x, y(op.y + op.h), op.w, op.h)
                cs.stroke()
            }
            is Op.Line -> {
                stroke(cs, op.width, op.ink.gray, op.dash, round = false)
                cs.moveTo(op.x1, y(op.y1))
                cs.lineTo(op.x2, y(op.y2))
                cs.stroke()
            }
            is Op.Polyline -> {
                stroke(cs, op.width, op.ink.gray, op.dash, round = true)
                op.points.forEachIndexed { i, p -> if (i == 0) cs.moveTo(p.x, y(p.y)) else cs.lineTo(p.x, y(p.y)) }
                cs.stroke()
            }
            is Op.Circle -> {
                circle(cs, op.cx, y(op.cy), op.r)
                if (op.strokeWidth > 0f) {
                    stroke(cs, op.strokeWidth, op.ink.gray, emptyList(), round = false)
                    cs.stroke()
                } else {
                    cs.setNonStrokingColor(op.ink.gray)
                    cs.fill()
                }
            }
            is Op.Text -> {
                cs.beginText()
                cs.setNonStrokingColor(op.ink.gray)
                cs.setFont(font(op.face), op.size)
                cs.setCharacterSpacing(op.letterSpacing)
                cs.newLineAtOffset(op.x, y(op.baseline))
                cs.showText(op.text)
                cs.endText()
            }
        }
    }

    private fun stroke(cs: PDPageContentStream, width: Float, gray: Float, dash: List<Float>, round: Boolean) {
        cs.setStrokingColor(gray)
        cs.setLineWidth(width)
        cs.setLineCapStyle(0)
        cs.setLineJoinStyle(if (round) 1 else 0)
        cs.setLineDashPattern(dash.toFloatArray(), 0f)
    }

    /** Four cubic Béziers; k = 4(√2 − 1)/3 keeps radial error under 0.03%. */
    private fun circle(cs: PDPageContentStream, cx: Float, cy: Float, r: Float) {
        val k = 0.5522848f * r
        cs.moveTo(cx + r, cy)
        cs.curveTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r)
        cs.curveTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy)
        cs.curveTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r)
        cs.curveTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy)
        cs.closePath()
    }
}

package io.github.couchknight.healthnote.render.android

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import io.github.couchknight.healthnote.render.DocumentDrawing
import io.github.couchknight.healthnote.render.pdf.PdfWriter

/** Entry point for the app: pdfbox-android must load its resources once before first use. */
object AndroidPdf {
    fun init(context: Context) {
        if (!PDFBoxResourceLoader.isReady()) PDFBoxResourceLoader.init(context.applicationContext)
    }

    fun render(context: Context, doc: DocumentDrawing): ByteArray {
        init(context)
        return PdfWriter.toBytes(doc)
    }
}

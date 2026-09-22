package io.github.couchknight.healthnote.render

import java.io.InputStream
import java.nio.ByteBuffer

/** The six faces the document uses. Files are SIL OFL 1.1, bundled under resources/fonts. */
enum class Face(val file: String) {
    FRANKLIN_LIGHT("libre_franklin_light.ttf"),
    FRANKLIN_REGULAR("libre_franklin_regular.ttf"),
    FRANKLIN_MEDIUM("libre_franklin_medium.ttf"),
    FRANKLIN_SEMIBOLD("libre_franklin_semibold.ttf"),
    CASLON_REGULAR("libre_caslon_text_regular.ttf"),
    CASLON_BOLD("libre_caslon_text_bold.ttf"),
    ;

    fun open(): InputStream =
        Face::class.java.getResourceAsStream("/fonts/$file") ?: error("missing font resource fonts/$file")
}

/**
 * Horizontal metrics read straight from the TrueType tables, the same `hmtx` advances PDFBox
 * writes into the PDF's width array, so measured and drawn text agree exactly. No kerning is
 * applied on either side.
 */
class FontMetrics private constructor(
    private val unitsPerEm: Int,
    /** hhea ascender and |descender|, per em. CSS uses these for line-box placement. */
    val ascent: Float,
    val descent: Float,
    private val advances: IntArray,
    private val cmap: Map<Int, Int>,
) {
    fun hasGlyph(codePoint: Int): Boolean = cmap[codePoint]?.let { it != 0 } ?: false

    /** Width of [text] at [size], with CSS letter-spacing (added after every character). */
    fun width(text: String, size: Float, letterSpacing: Float = 0f): Float {
        var units = 0L
        var count = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val gid = cmap[cp] ?: 0
            units += advances[minOf(gid, advances.size - 1)]
            count++
            i += Character.charCount(cp)
        }
        return units * size / unitsPerEm + count * letterSpacing
    }

    companion object {
        private val cache = HashMap<Face, FontMetrics>()

        @Synchronized
        fun of(face: Face): FontMetrics = cache.getOrPut(face) { face.open().use { parse(it.readBytes()) } }

        fun parse(bytes: ByteArray): FontMetrics {
            val b = ByteBuffer.wrap(bytes)
            val numTables = b.getShort(4).toInt() and 0xFFFF
            val tables = HashMap<String, Int>()
            for (t in 0 until numTables) {
                val rec = 12 + t * 16
                val tag = String(bytes, rec, 4, Charsets.US_ASCII)
                tables[tag] = b.getInt(rec + 8)
            }
            fun table(tag: String) = tables[tag] ?: error("font has no $tag table")

            val head = table("head")
            val upm = b.getShort(head + 18).toInt() and 0xFFFF
            val hhea = table("hhea")
            val ascender = b.getShort(hhea + 4).toInt()
            val descender = b.getShort(hhea + 6).toInt()
            val numHMetrics = b.getShort(hhea + 34).toInt() and 0xFFFF
            val hmtx = table("hmtx")
            val advances = IntArray(numHMetrics) { b.getShort(hmtx + it * 4).toInt() and 0xFFFF }
            return FontMetrics(
                unitsPerEm = upm,
                ascent = ascender / upm.toFloat(),
                descent = -descender / upm.toFloat(),
                advances = advances,
                cmap = readCmap(b, table("cmap")),
            )
        }

        /** Unicode BMP mapping from the (3,1) format-4 subtable. */
        private fun readCmap(b: ByteBuffer, cmap: Int): Map<Int, Int> {
            val n = b.getShort(cmap + 2).toInt() and 0xFFFF
            var sub = -1
            for (i in 0 until n) {
                val rec = cmap + 4 + i * 8
                val platform = b.getShort(rec).toInt()
                val encoding = b.getShort(rec + 2).toInt()
                val offset = b.getInt(rec + 4)
                if (platform == 3 && encoding == 1 && (b.getShort(cmap + offset).toInt() == 4)) sub = cmap + offset
            }
            check(sub >= 0) { "font has no (3,1) format 4 cmap" }
            val segCount = (b.getShort(sub + 6).toInt() and 0xFFFF) / 2
            val ends = sub + 14
            val starts = ends + segCount * 2 + 2
            val deltas = starts + segCount * 2
            val rangeOffsets = deltas + segCount * 2
            val out = HashMap<Int, Int>()
            for (s in 0 until segCount) {
                val end = b.getShort(ends + s * 2).toInt() and 0xFFFF
                val start = b.getShort(starts + s * 2).toInt() and 0xFFFF
                val delta = b.getShort(deltas + s * 2).toInt()
                val ro = b.getShort(rangeOffsets + s * 2).toInt() and 0xFFFF
                if (start == 0xFFFF) continue
                for (c in start..end) {
                    val gid = if (ro == 0) {
                        (c + delta) and 0xFFFF
                    } else {
                        val at = rangeOffsets + s * 2 + ro + (c - start) * 2
                        val g = b.getShort(at).toInt() and 0xFFFF
                        if (g == 0) 0 else (g + delta) and 0xFFFF
                    }
                    if (gid != 0) out[c] = gid
                }
            }
            return out
        }
    }
}

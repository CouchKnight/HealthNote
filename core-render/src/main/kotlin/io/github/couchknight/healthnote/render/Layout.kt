package io.github.couchknight.healthnote.render

/** Page box and the chrome every page shares, in units (DESIGN.md §4.1). */
object Page {
    const val W = 1404f
    const val H = 1872f
    const val PAD_TOP = 96f
    const val PAD_X = 88f
    const val LEFT = PAD_X
    const val RIGHT = W - PAD_X
    const val CONTENT_W = W - 2 * PAD_X

    /** Footer nav: 3-unit rule plus 30 + 30 + 30 of padded label. */
    const val FOOTER_H = 93f
    const val FOOTER_TOP = H - FOOTER_H

    /** Hairlines never thinner than 2 units (DESIGN.md §4.1). */
    const val HAIRLINE = 2f
}

enum class Align { LEFT, CENTER, RIGHT }

/** A CSS `font:` shorthand plus colour, tracking (em) and text-transform. */
data class Style(
    val face: Face,
    val size: Float,
    val ink: Ink = Ink.BLACK,
    val lineHeight: Float = 1f,
    val tracking: Float = 0f,
    val upper: Boolean = false,
) {
    val metrics: FontMetrics get() = FontMetrics.of(face)
    val letterSpacing: Float get() = tracking * size
    val lineBox: Float get() = lineHeight * size

    /**
     * The string as drawn: case-transformed, with characters the face cannot draw replaced by
     * "?". PDFBox throws on a missing glyph, and one emoji in a medication name must not stop
     * the daily publish.
     */
    fun display(s: String): String {
        val t = if (upper) s.uppercase() else s
        val m = metrics
        if (t.codePoints().allMatch { m.hasGlyph(it) }) return t
        val out = StringBuilder()
        t.codePoints().forEach { cp -> if (m.hasGlyph(cp)) out.appendCodePoint(cp) else out.append('?') }
        return out.toString()
    }

    fun width(s: String): Float = metrics.width(display(s), size, letterSpacing)

    /** Baseline of a line box whose top is [top]: CSS half-leading on hhea ascent/descent. */
    fun baseline(top: Float): Float {
        val m = metrics
        return top + (lineBox - (m.ascent + m.descent) * size) / 2f + m.ascent * size
    }

    fun with(ink: Ink) = copy(ink = ink)
}

/** Styles lifted from the design's CSS, named by role. */
object Styles {
    val label = Style(Face.FRANKLIN_MEDIUM, 30f, Ink.DARK, tracking = .12f, upper = true)
    val caption = Style(Face.FRANKLIN_LIGHT, 30f, Ink.DARK)
    val headerTitle = Style(Face.CASLON_BOLD, 80f, tracking = -.02f)
    val headerRight = Style(Face.FRANKLIN_LIGHT, 34f, Ink.DARK)
    val nav = Style(Face.FRANKLIN_MEDIUM, 30f, Ink.DARK, tracking = .06f)
}

/** Collects ops for one layer of a page. */
class Layer {
    val ops = mutableListOf<Op>()

    fun rect(x: Float, y: Float, w: Float, h: Float, ink: Ink) {
        ops += Op.FillRect(x, y, w, h, ink)
    }

    /** A horizontal rule whose top edge is [y] (CSS border). */
    fun hrule(x: Float, y: Float, w: Float, thickness: Float = Page.HAIRLINE, ink: Ink = Ink.MID) =
        rect(x, y, w, thickness, ink)

    fun vrule(x: Float, y: Float, h: Float, thickness: Float = Page.HAIRLINE, ink: Ink = Ink.MID) =
        rect(x, y, thickness, h, ink)

    /** A CSS border drawn inside the box, as four rects so corners stay square and crisp. */
    fun border(x: Float, y: Float, w: Float, h: Float, t: Float, ink: Ink) {
        rect(x, y, w, t, ink)
        rect(x, y + h - t, w, t, ink)
        rect(x, y + t, t, h - 2 * t, ink)
        rect(x + w - t, y + t, t, h - 2 * t, ink)
    }

    /** A disc of diameter [d] whose bounding box starts at ([x], [y]). */
    fun disc(x: Float, y: Float, d: Float, ink: Ink) {
        ops += Op.Circle(x + d / 2, y + d / 2, d / 2, ink)
    }

    /** A ring of outer diameter [d] with a CSS `border` of [t] (border-box sizing). */
    fun ring(x: Float, y: Float, d: Float, t: Float, ink: Ink) {
        ops += Op.Circle(x + d / 2, y + d / 2, d / 2 - t / 2, ink, t)
    }

    /**
     * One line of text whose line box starts at [top]. With [Align.RIGHT], [x] is the right
     * edge; with [Align.CENTER], [x]..[x]+[boxW] is the box. Returns the drawn width.
     */
    fun text(
        style: Style,
        s: String,
        x: Float,
        top: Float,
        align: Align = Align.LEFT,
        boxW: Float = 0f,
    ): Float {
        if (s.isEmpty()) return 0f
        val shown = style.display(s)
        val w = style.width(s)
        val left = when (align) {
            Align.LEFT -> x
            Align.RIGHT -> x - w
            Align.CENTER -> x + (boxW - w) / 2
        }
        ops += Op.Text(shown, style.face, style.size, left, style.baseline(top), style.ink, style.letterSpacing)
        return w
    }

    /** Wrapped text; returns the number of lines drawn. Lines past [maxLines] are cut with "…". */
    fun paragraph(
        style: Style,
        s: String,
        x: Float,
        top: Float,
        width: Float,
        maxLines: Int = Int.MAX_VALUE,
        align: Align = Align.LEFT,
    ): Int {
        val lines = wrap(style, s, width, maxLines)
        lines.forEachIndexed { i, line ->
            val lineTop = top + i * style.lineBox
            when (align) {
                Align.LEFT -> text(style, line, x, lineTop)
                Align.RIGHT -> text(style, line, x + width, lineTop, Align.RIGHT)
                Align.CENTER -> text(style, line, x, lineTop, Align.CENTER, width)
            }
        }
        return lines.size
    }
}

/** Greedy word wrap on spaces. A single word wider than [width] gets a line to itself. */
fun wrap(style: Style, s: String, width: Float, maxLines: Int = Int.MAX_VALUE): List<String> {
    val out = mutableListOf<String>()
    for (para in s.split('\n')) {
        var line = ""
        for (word in para.split(' ').filter { it.isNotEmpty() }) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (line.isEmpty() || style.width(candidate) <= width) {
                line = candidate
            } else {
                out += line
                line = word
            }
        }
        out += line
    }
    if (out.size <= maxLines) return out
    val kept = out.take(maxLines).toMutableList()
    var last = kept.last() + "…"
    while (style.width(last) > width && last.length > 1) last = last.dropLast(2) + "…"
    kept[kept.lastIndex] = last
    return kept
}

fun lineCount(style: Style, s: String, width: Float): Int = wrap(style, s, width).size

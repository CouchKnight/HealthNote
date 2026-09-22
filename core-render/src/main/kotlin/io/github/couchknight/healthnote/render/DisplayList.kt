package io.github.couchknight.healthnote.render

/**
 * Pure black plus three greys (DESIGN.md §4.1): e-ink dithers continuous tone, so the page
 * only ever uses these five values. Emitted as DeviceGray.
 */
enum class Ink(val gray: Float) {
    BLACK(0f),
    DARK(0x55 / 255f),
    MID(0xCC / 255f),
    LIGHT(0xEE / 255f),
    WHITE(1f),
}

/**
 * Drawing operations in page units, origin top-left, y down (1 unit = 1 Nomad pixel).
 * The PDF backend flips to PDF's bottom-left origin.
 */
sealed interface Op {
    data class FillRect(val x: Float, val y: Float, val w: Float, val h: Float, val ink: Ink) : Op

    data class Line(
        val x1: Float, val y1: Float, val x2: Float, val y2: Float,
        val width: Float, val ink: Ink, val dash: List<Float> = emptyList(),
    ) : Op

    data class Polyline(
        val points: List<Pt>, val width: Float, val ink: Ink, val dash: List<Float> = emptyList(),
    ) : Op

    /** A filled disc, or a ring when [strokeWidth] > 0 (stroke centred on [r]). */
    data class Circle(val cx: Float, val cy: Float, val r: Float, val ink: Ink, val strokeWidth: Float = 0f) : Op

    /** An outlined rectangle; the stroke is centred on the given edges. */
    data class StrokeRect(val x: Float, val y: Float, val w: Float, val h: Float, val width: Float, val ink: Ink) : Op

    /** Single-line text: [x] is the left edge, [baseline] the baseline, [letterSpacing] in units. */
    data class Text(
        val text: String, val face: Face, val size: Float, val x: Float, val baseline: Float,
        val ink: Ink, val letterSpacing: Float = 0f,
    ) : Op
}

data class Pt(val x: Float, val y: Float)

data class Rect(val x: Float, val y: Float, val w: Float, val h: Float)

/** A `/GoTo` link rectangle to page [target] (0-based). */
data class Link(val rect: Rect, val target: Int)

/**
 * One page, split the way the geometry invariant needs (DESIGN.md §1.3):
 *
 * - [frame] and [links] are a function of (month, config) only: rules, labels, grids, nav.
 * - [paint] is everything that depends on logged doses and sleep, drawn inside cells the
 *   frame already fixed.
 *
 * Ink written on the tablet is anchored to page coordinates, so the frame must not move
 * between the daily regenerations of a month. The golden test asserts exactly that.
 */
data class PageDrawing(
    val title: String,
    val frame: List<Op>,
    val paint: List<Op>,
    val links: List<Link>,
)

data class DocumentDrawing(val fileName: String, val title: String, val pages: List<PageDrawing>)

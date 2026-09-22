package io.github.couchknight.healthnote.render

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LayoutTest {
    private val body = Style(Face.FRANKLIN_REGULAR, 40f)

    @Test
    fun `characters the face lacks are drawn as a question mark`() {
        assertEquals("Iron ? 65 mg", body.display("Iron 💊 65 mg"))
        assertEquals("23:24?06:36", body.display("23:24→06:36"))
        assertEquals("Vitamin C", body.display("Vitamin C"))
    }

    @Test
    fun `measured width includes css letter spacing after every character`() {
        val plain = Style(Face.FRANKLIN_MEDIUM, 30f)
        val tracked = plain.copy(tracking = .1f)
        assertEquals(plain.width("HUB") + 3 * 3f, tracked.width("HUB"), 0.001f)
    }

    @Test
    fun `line box baseline follows css half-leading`() {
        // Libre Franklin hhea: ascent 966, descent 246 per 1000. At 30/1 the content area is
        // 36.36, so the half-leading is -3.18 and the baseline sits 25.8 below the box top.
        assertEquals(25.8f, Style(Face.FRANKLIN_LIGHT, 30f).baseline(0f), 0.01f)
    }

    @Test
    fun `wrap breaks on spaces and cuts overflow with an ellipsis`() {
        val lines = wrap(Styles.caption, "one two three four five six seven eight nine ten", 200f)
        assertTrue(lines.size > 1)
        lines.forEach { assertTrue(Styles.caption.width(it) <= 200f || ' ' !in it) }
        val cut = wrap(Styles.caption, "one two three four five six seven eight nine ten", 200f, maxLines = 2)
        assertEquals(2, cut.size)
        assertTrue(cut.last().endsWith("…"))
    }
}

package io.github.couchknight.healthnote.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.couchknight.healthnote.app.R

/** Warm paper and ink indigo, from the HealthNote App design. */
object HN {
    val Paper = Color(0xFFF4F0E8)
    val Card = Color(0xFFFFFCF6)
    val Ink = Color(0xFF191D33)
    val Indigo = Color(0xFF2E3A86)
    val IndigoPressed = Color(0xFF252E6D)
    val Muted = Color(0xFF575B76)

    /**
     * Secondary text. The mockup's #8a8da3 failed contrast; the design review replaced it with
     * this (4.96:1 on Paper) everywhere, including tab labels and unselected day chips.
     */
    val Subtle = Color(0xFF62667E)

    val Amber = Color(0xFFB06A22)
    val Teal = Color(0xFF2F6B63)
    val Rose = Color(0xFF8F3B2A)

    val TrackOff = Color(0xFFC9C3B6)
    val BarBg = Color(0xFFDED8CA)
    val DocCard = Color(0xFFECEADF)
    val DocCardPressed = Color(0xFFE6E3D5)
    val AllClear = Color(0xFFEEF0F8)
    val WarnBg = Color(0xFFF6EFE4)
    val WarnTitle = Color(0xFF8F5619)
    val WarnBody = Color(0xFF7A5A36)

    val SleepBar = Color(0xFF8D93B8)
    val SleepShort = Color(0xFFB9563C)
    val StageDeep = Color(0xFF191D33)
    val StageRem = Color(0xFF4A5299)
    val StageLight = Color(0xFF9AA0C4)
    val StageAwake = Color(0xFFDED8CA)

    fun ink(alpha: Float) = Ink.copy(alpha = alpha)
    val Rule = ink(.12f)
    val RuleSoft = ink(.09f)
    val RuleStrong = ink(.22f)
    val CardBorder = ink(.10f)

    /** Phone colour per schedule `colorSlot`. The tablet ignores these (black plus greys). */
    val MedColors = listOf(Indigo, Amber, Teal, Rose)

    fun medColor(slot: Int) = MedColors[slot.mod(MedColors.size)]
}

val Franklin = FontFamily(
    Font(R.font.libre_franklin_light, FontWeight.Light),
    Font(R.font.libre_franklin_regular, FontWeight.Normal),
    Font(R.font.libre_franklin_medium, FontWeight.Medium),
    Font(R.font.libre_franklin_semibold, FontWeight.SemiBold),
)

val Caslon = FontFamily(Font(R.font.libre_caslon_text_regular, FontWeight.Normal))

/** CSS `font: <weight> <size>/<line-height>` from the mockup, as a TextStyle. */
fun franklin(
    weight: FontWeight,
    size: Float,
    lineHeight: Float = 1.25f,
    color: Color = HN.Ink,
    tracking: Float = 0f,
): TextStyle = TextStyle(
    fontFamily = Franklin,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.em,
    letterSpacing = if (tracking == 0f) TextUnit.Unspecified else tracking.em,
    color = color,
)

fun caslon(size: Float, lineHeight: Float = 1.15f, color: Color = HN.Ink, tracking: Float = 0f): TextStyle = TextStyle(
    fontFamily = Caslon,
    fontWeight = FontWeight.Normal,
    fontSize = size.sp,
    lineHeight = lineHeight.em,
    letterSpacing = if (tracking == 0f) TextUnit.Unspecified else tracking.em,
    color = color,
)

/** Recurring roles. */
object Type {
    val sectionLabel = franklin(FontWeight.SemiBold, 11f, 1f, HN.Muted, tracking = .14f)
    val fieldLabel = franklin(FontWeight.SemiBold, 10f, 1f, HN.Subtle, tracking = .14f)
    val tileLabel = franklin(FontWeight.SemiBold, 10f, 1f, HN.Subtle, tracking = .13f)
    val body = franklin(FontWeight.Light, 13f, 1.55f, HN.Muted)
    val note = franklin(FontWeight.Light, 11f, 1.5f, HN.Subtle)
    val small = franklin(FontWeight.Light, 11f, 1.35f, HN.Muted)
}

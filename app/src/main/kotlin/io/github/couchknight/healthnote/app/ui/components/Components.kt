package io.github.couchknight.healthnote.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.couchknight.healthnote.app.ui.Screen
import io.github.couchknight.healthnote.app.ui.theme.HN
import io.github.couchknight.healthnote.app.ui.theme.Type
import io.github.couchknight.healthnote.app.ui.theme.caslon
import io.github.couchknight.healthnote.app.ui.theme.franklin

val Radius2 = RoundedCornerShape(2.dp)
val Radius3 = RoundedCornerShape(3.dp)

@Composable
fun HRule(color: Color = HN.Rule, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    BasicText(text.uppercase(), modifier, style = Type.sectionLabel)
}

/** The white card with a hairline border used for due doses, tiles and the NFC row. */
fun Modifier.card(selected: Boolean = false, fill: Color = HN.Card): Modifier =
    this.background(fill, Radius3).border(1.dp, if (selected) HN.Indigo else HN.CardBorder, Radius3)

/** Clickable without the platform ripple; pressed state is drawn by the caller. */
@Composable
fun Modifier.tap(role: Role = Role.Button, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = source, indication = null, role = role, onClick = onClick)
}

@Composable
fun PrimaryButton(text: String, height: Dp = 48.dp, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .background(if (pressed) HN.IndigoPressed else HN.Indigo, Radius2)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = franklin(FontWeight.Medium, if (height > 50.dp) 15f else 14f, 1f, HN.Card))
    }
}

@Composable
fun OutlineButton(
    text: String,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    pressedColor: Color = HN.Rose,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier
            .height(height)
            .border(1.dp, if (pressed) pressedColor else HN.RuleStrong, Radius2)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = franklin(FontWeight.Normal, 14f, 1f, if (pressed) pressedColor else HN.Muted))
    }
}

@Composable
fun Switch(on: Boolean, onToggle: () -> Unit) {
    Box(
        Modifier
            .size(46.dp, 26.dp)
            .background(if (on) HN.Indigo else HN.TrackOff, RoundedCornerShape(13.dp))
            .tap(Role.Switch, onToggle)
            .padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(20.dp).background(HN.Card, CircleShape))
    }
}

/** A dashed 1dp border, which Modifier.border cannot draw. */
fun Modifier.dashedBorder(color: Color, radius: Dp = 3.dp): Modifier = drawBehind {
    val stroke = 1.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
        size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
    )
}

/** The med marker ring: hollow for due, used on Today and in the confirm sheet. */
@Composable
fun MedRing(color: Color, size: Dp = 26.dp) {
    Box(Modifier.size(size).border(2.dp, color, CircleShape))
}

@Composable
fun MedDot(color: Color, size: Dp = 9.dp) {
    Box(Modifier.size(size).background(color, CircleShape))
}

@Composable
fun Header(title: String, date: String, canBack: Boolean, syncLine: String?, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(HN.Paper).padding(start = 20.dp, end = 20.dp, top = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (canBack) {
                BasicText(
                    "←",
                    Modifier.tap(onClick = onBack).padding(top = 4.dp, bottom = 4.dp, end = 8.dp),
                    style = franklin(FontWeight.Medium, 13f, 1f, HN.Indigo),
                )
            }
            BasicText(title, Modifier.weight(1f), style = caslon(20f, 1.1f, tracking = -.01f))
            BasicText(date.uppercase(), style = franklin(FontWeight.Normal, 10f, 1f, HN.Subtle, tracking = .1f))
        }
        if (syncLine != null) {
            Row(Modifier.padding(top = 9.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(5.dp).background(HN.Teal, CircleShape))
                Spacer(Modifier.width(7.dp))
                BasicText(syncLine, style = franklin(FontWeight.Light, 11f, 1.3f, HN.Muted))
            }
        } else {
            Spacer(Modifier.height(10.dp))
        }
        HRule()
    }
}

private val Tabs = listOf(Screen.TODAY to "Today", Screen.MEDS to "Meds", Screen.SLEEP to "Sleep", Screen.DOC to "Document")

@Composable
fun BottomTabs(current: Screen, onSelect: (Screen) -> Unit) {
    Column(Modifier.fillMaxWidth().background(HN.Paper)) {
        HRule()
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 6.dp)) {
            for ((screen, label) in Tabs) {
                val active = current == screen || current.parent == screen
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .tap(Role.Tab) { onSelect(screen) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterVertically),
                ) {
                    Box(Modifier.size(20.dp, 2.dp).background(if (active) HN.Indigo else Color.Transparent))
                    BasicText(
                        label,
                        style = franklin(FontWeight.Medium, 11f, 1f, if (active) HN.Ink else HN.Subtle, tracking = .03f)
                            .copy(textAlign = TextAlign.Center),
                    )
                }
            }
        }
    }
}

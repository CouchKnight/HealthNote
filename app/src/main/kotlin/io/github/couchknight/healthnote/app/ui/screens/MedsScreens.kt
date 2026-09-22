package io.github.couchknight.healthnote.app.ui.screens

import android.app.TimePickerDialog
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.couchknight.healthnote.app.ui.ScheduleDraft
import io.github.couchknight.healthnote.app.ui.UiState
import io.github.couchknight.healthnote.app.ui.components.HRule
import io.github.couchknight.healthnote.app.ui.components.MedDot
import io.github.couchknight.healthnote.app.ui.components.OutlineButton
import io.github.couchknight.healthnote.app.ui.components.PrimaryButton
import io.github.couchknight.healthnote.app.ui.components.Radius2
import io.github.couchknight.healthnote.app.ui.components.Switch
import io.github.couchknight.healthnote.app.ui.components.card
import io.github.couchknight.healthnote.app.ui.components.dashedBorder
import io.github.couchknight.healthnote.app.ui.components.tap
import io.github.couchknight.healthnote.app.ui.theme.HN
import io.github.couchknight.healthnote.app.ui.theme.Type
import io.github.couchknight.healthnote.app.ui.theme.caslon
import io.github.couchknight.healthnote.app.ui.theme.franklin
import io.github.couchknight.healthnote.model.Formats
import io.github.couchknight.healthnote.model.MedReport
import io.github.couchknight.healthnote.model.SlotState
import io.github.couchknight.healthnote.model.doseTagUri
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.YearMonth

private val NUMBER_WORDS = listOf("No", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten")

@Composable
fun MedsScreen(state: UiState, onEdit: (String?) -> Unit, onNfc: () -> Unit) {
    val meds = state.report.meds
    Column(
        Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        val count = NUMBER_WORDS.getOrNull(meds.size) ?: meds.size.toString()
        BasicText(
            "$count active schedule${if (meds.size == 1) "" else "s"}. Logging is idempotent per slot, so a " +
                "double tap or a second NFC scan can never create a second event.",
            style = Type.body,
        )
        Column {
            for (m in meds) MedRow(m) { onEdit(m.schedule.id) }
            HRule()
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth().card().tap(onClick = onNfc).padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(22.dp).border(2.dp, HN.Indigo, CircleShape), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(8.dp).background(HN.Indigo, CircleShape))
                }
                BasicText("Write an NFC tag", Modifier.weight(1f), style = franklin(FontWeight.Medium, 14f, 1.2f))
                BasicText("›", style = franklin(FontWeight.Normal, 14f, 1f, HN.Subtle))
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .dashedBorder(HN.ink(.28f))
                    .tap { onEdit(null) }
                    .padding(15.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText("+  Add a medication", style = franklin(FontWeight.Medium, 13f, 1f, HN.Indigo))
            }
        }
    }
}

@Composable
private fun MedRow(m: MedReport, onClick: () -> Unit) {
    val color = HN.medColor(m.schedule.colorSlot)
    val pct = m.counts.percent
    Column {
        HRule()
        Column(
            Modifier.fillMaxWidth().tap(onClick = onClick).padding(horizontal = 2.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MedDot(color)
                BasicText(m.schedule.name, Modifier.weight(1f), style = franklin(FontWeight.Medium, 16f, 1.2f))
                BasicText(pct?.let { "$it%" } ?: "—", style = franklin(FontWeight.Normal, 13f, 1f, HN.Muted))
            }
            Row(Modifier.padding(start = 21.dp), verticalAlignment = Alignment.Bottom) {
                BasicText(
                    "${m.schedule.dose} · ${Formats.times(m.schedule.timesOfDay)} · ${Formats.days(m.schedule.daysOfWeek)}",
                    Modifier.weight(1f),
                    style = franklin(FontWeight.Light, 12f, 1.3f, HN.Muted),
                )
                BasicText(streakLabel(m), style = franklin(FontWeight.Light, 11f, 1.3f, HN.Subtle))
            }
            Box(Modifier.padding(start = 21.dp).fillMaxWidth().height(4.dp).background(HN.BarBg, Radius2)) {
                Box(Modifier.fillMaxWidth((pct ?: 0) / 100f).height(4.dp).background(color, Radius2))
            }
        }
    }
}

private fun streakLabel(m: MedReport): String = when {
    m.streakDays > 0 -> "${m.streakDays}-day streak"
    m.lastLapse != null -> {
        val verb = if (m.lastLapse!!.state == SlotState.SKIPPED) "Skipped" else "Missed"
        "$verb ${Formats.dayLabel(m.lastLapse!!.slot.date)}"
    }
    else -> ""
}

// Schedule editor ---------------------------------------------------------------------------

@Composable
fun EditScheduleScreen(
    draft: ScheduleDraft,
    month: YearMonth,
    onChange: ((ScheduleDraft) -> ScheduleDraft) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Field("Name") {
                UnderlinedInput(draft.name, caslon(19f, 1.2f), "Medication") { v -> onChange { it.copy(name = v) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Field("Dose", Modifier.weight(1f)) {
                    UnderlinedInput(draft.dose, franklin(FontWeight.Normal, 15f, 1.2f), "30 mg") { v -> onChange { it.copy(dose = v) } }
                }
                Field("Time", Modifier.weight(1f)) {
                    Column(
                        Modifier.fillMaxWidth().tap {
                            TimePickerDialog(
                                context,
                                { _, h, min -> onChange { it.copy(time = LocalTime.of(h, min)) } },
                                draft.time.hour, draft.time.minute, true,
                            ).show()
                        },
                    ) {
                        BasicText(
                            Formats.clock(draft.time),
                            Modifier.padding(top = 4.dp, bottom = 8.dp),
                            style = franklin(FontWeight.Normal, 15f, 1.2f),
                        )
                        HRule(HN.RuleStrong)
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText("DAYS OF WEEK", style = Type.fieldLabel)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DayOfWeek.entries.forEach { day ->
                    val on = day in draft.days
                    Box(
                        Modifier
                            .weight(1f)
                            .height(44.dp)
                            .background(if (on) HN.Indigo else Color.Transparent, Radius2)
                            .border(1.dp, if (on) HN.Indigo else HN.ink(.18f), Radius2)
                            .tap(Role.Checkbox) { onChange { d -> d.copy(days = if (on) d.days - day else d.days + day) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            Formats.weekday(day).take(1),
                            style = franklin(FontWeight.Medium, 12f, 1f, if (on) HN.Card else HN.Subtle),
                        )
                    }
                }
            }
            BasicText(daysSummary(draft.days, month), style = franklin(FontWeight.Light, 11f, 1.4f, HN.Subtle))
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText("MARKER ON THE TABLET", style = Type.fieldLabel)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HN.MedColors.forEachIndexed { i, c ->
                    Box(
                        Modifier
                            .size(44.dp)
                            .border(2.dp, if (draft.colorSlot == i) HN.Ink else Color.Transparent, CircleShape)
                            .tap(Role.RadioButton) { onChange { it.copy(colorSlot = i) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(22.dp).background(c, CircleShape))
                    }
                }
            }
            BasicText(
                "Rendered as black plus two greys on e-ink. Colour only separates rows here.",
                style = franklin(FontWeight.Light, 11f, 1.4f, HN.Subtle),
            )
        }

        Column {
            HRule()
            Row(
                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    BasicText("Reminder at the scheduled time", style = franklin(FontWeight.Medium, 14f, 1.2f))
                    BasicText("Taken / Skip / Snooze 15m, handled without opening the app", style = Type.small)
                }
                Switch(draft.remind) { onChange { it.copy(remind = !it.remind) } }
            }
            HRule()
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(if (draft.isNew) "Add schedule" else "Save schedule", modifier = Modifier.weight(1f), onClick = onSave)
            OutlineButton(if (draft.isNew) "Cancel" else "Delete", Modifier.width(110.dp), onClick = onDelete)
        }
        BasicText(
            "Changing a schedule changes future slots only. Past dose events keep the schedule they were " +
                "logged against, so adherence history never moves.",
            style = Type.note,
        )
    }
}

@Composable
private fun Field(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(label.uppercase(), style = Type.fieldLabel)
        content()
    }
}

@Composable
private fun UnderlinedInput(
    value: String,
    style: androidx.compose.ui.text.TextStyle,
    placeholder: String,
    onValue: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(HN.Indigo),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) BasicText(placeholder, style = style.copy(color = HN.Subtle))
                    inner()
                }
            },
        )
        HRule(HN.RuleStrong)
    }
}

/** Counts the month's real slots; the mockup approximated with days x 4 + 2. */
private fun daysSummary(days: Set<DayOfWeek>, month: YearMonth): String {
    val slots = (1..month.lengthOfMonth()).count { month.atDay(it).dayOfWeek in days }
    val name = Formats.monthName(month)
    return when (days.size) {
        7 -> "Every day — $slots slots in $name"
        0 -> "No days selected; the schedule is inactive"
        else -> "${days.size} days a week — $slots slots in $name"
    }
}

// NFC tag writer ----------------------------------------------------------------------------

@Composable
fun NfcScreen(state: UiState, onPick: (String) -> Unit) {
    val selected = state.tagScheduleId
    Column(
        Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        BasicText(
            buildAnnotatedString {
                append("Writes ")
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = HN.Ink)) {
                    append(selected?.let { doseTagUri(it) } ?: doseTagUri("<id>"))
                }
                append(" to a tag. Stick it on the pill box; a tap logs the dose and closes.")
            },
            style = Type.body,
        )

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            BasicText("TAG LOGS", style = Type.fieldLabel)
            for (m in state.report.meds) {
                val on = m.schedule.id == selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .card(selected = on, fill = if (on) HN.AllClear else HN.Card)
                        .tap(Role.RadioButton) { onPick(m.schedule.id) }
                        .padding(horizontal = 15.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(13.dp),
                ) {
                    MedDot(HN.medColor(m.schedule.colorSlot))
                    BasicText(m.schedule.name, Modifier.weight(1f), style = franklin(FontWeight.Normal, 14f, 1.2f))
                    BasicText(Formats.times(m.schedule.timesOfDay), style = franklin(FontWeight.Light, 11.5f, 1f, HN.Subtle))
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NfcPulse()
            BasicText(
                "Hold the tag against the back of the phone",
                style = caslon(16f, 1.3f).copy(textAlign = TextAlign.Center),
            )
            BasicText(
                "Android only reads tags with the screen on and the phone unlocked. A tap while locked does " +
                    "nothing, and that is not something the app can fix.",
                Modifier.widthIn(max = 270.dp),
                style = franklin(FontWeight.Light, 11.5f, 1.5f, HN.Subtle).copy(textAlign = TextAlign.Center),
            )
        }
    }
}

/** Two rings expanding out of the READY disc, 1.2 s apart (CSS pulseRing, 2.4 s ease-out). */
@Composable
private fun NfcPulse() {
    val transition = rememberInfiniteTransition(label = "nfc")
    val rings = listOf(0, 1200).map { delay ->
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(2400, easing = LinearOutSlowInEasing),
                RepeatMode.Restart,
                initialStartOffset = StartOffset(delay),
            ),
            label = "ring$delay",
        )
    }
    Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
        for (ring in rings) {
            val t by ring
            Box(
                Modifier
                    .fillMaxSize()
                    .scale(.7f + .8f * t)
                    .alpha(.55f * (1f - t))
                    .border(1.dp, HN.Indigo, CircleShape),
            )
        }
        Box(Modifier.size(72.dp).background(HN.Indigo, CircleShape), contentAlignment = Alignment.Center) {
            BasicText(
                "NFC\nREADY",
                style = franklin(FontWeight.Normal, 11f, 1.3f, HN.Card, tracking = .08f).copy(textAlign = TextAlign.Center),
            )
        }
    }
}

package io.github.couchknight.healthnote.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.couchknight.healthnote.app.ui.UiState
import io.github.couchknight.healthnote.app.ui.components.HRule
import io.github.couchknight.healthnote.app.ui.components.MedRing
import io.github.couchknight.healthnote.app.ui.components.Radius2
import io.github.couchknight.healthnote.app.ui.components.Radius3
import io.github.couchknight.healthnote.app.ui.components.SectionLabel
import io.github.couchknight.healthnote.app.ui.components.card
import io.github.couchknight.healthnote.app.ui.components.tap
import io.github.couchknight.healthnote.app.ui.theme.HN
import io.github.couchknight.healthnote.app.ui.theme.Type
import io.github.couchknight.healthnote.app.ui.theme.caslon
import io.github.couchknight.healthnote.app.ui.theme.franklin
import io.github.couchknight.healthnote.model.DoseSlot
import io.github.couchknight.healthnote.model.DoseSource
import io.github.couchknight.healthnote.model.Formats
import io.github.couchknight.healthnote.model.SlotState
import java.time.Duration

@Composable
fun TodayScreen(
    state: UiState,
    onDose: (scheduleId: String, slot: DoseSlot) -> Unit,
    onSleep: () -> Unit,
    onDocument: () -> Unit,
) {
    val r = state.report
    Column(
        Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Hero(state)
        HRule()

        val open = r.todaySlots.filter { it.second.state == SlotState.DUE || it.second.state == SlotState.UPCOMING }
        if (open.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    SectionLabel(if (open.any { it.second.state == SlotState.DUE }) "Due now" else "Later today", Modifier.weight(1f))
                    BasicText("${open.size} of ${r.todaySlots.size} today", style = franklin(FontWeight.Light, 11f, 1f, HN.Subtle))
                }
                for ((med, slot) in open) {
                    val color = HN.medColor(med.schedule.colorSlot)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .card()
                            .tap { onDose(med.schedule.id, slot.slot) }
                            .padding(horizontal = 16.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        MedRing(color)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            BasicText(med.schedule.name, style = franklin(FontWeight.Medium, 15f))
                            BasicText(
                                "${med.schedule.dose} · scheduled ${Formats.clock(slot.slot.scheduledFor.toLocalTime())}",
                                style = franklin(FontWeight.Light, 12f, color = HN.Muted),
                            )
                        }
                        BasicText("LOG", style = franklin(FontWeight.Medium, 11f, 1f, HN.Indigo, tracking = .1f))
                    }
                }
            }
        } else if (r.todaySlots.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().background(HN.AllClear, Radius3).padding(horizontal = 17.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BasicText("Everything for today is in.", style = caslon(16f, 1.25f, HN.Indigo))
                BasicText(
                    "${nextDoseLine(state)}The tablet picks up the change on its next sync.",
                    style = franklin(FontWeight.Light, 12f, 1.45f, HN.Muted),
                )
            }
        }

        val logged = r.todaySlots.filter { it.second.state == SlotState.TAKEN || it.second.state == SlotState.SKIPPED }
        if (logged.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Logged today")
                for ((med, slot) in logged.sortedByDescending { it.second.event?.loggedAt }) {
                    val color = HN.medColor(med.schedule.colorSlot)
                    val skipped = slot.state == SlotState.SKIPPED
                    val slotTime = Formats.clock(slot.slot.scheduledFor.toLocalTime())
                    val at = slot.event?.loggedAt?.toLocalTime()?.let { Formats.clock(it) } ?: ""
                    Column {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            if (skipped) {
                                MedRing(color)
                            } else {
                                Box(Modifier.size(26.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
                                    BasicText("✓", style = franklin(FontWeight.Medium, 13f, 1f, HN.Card))
                                }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                BasicText("${med.schedule.name} ${med.schedule.dose}", style = franklin(FontWeight.Normal, 14f))
                                BasicText(
                                    if (skipped) "$slotTime slot · skipped" else "$slotTime slot · logged $at",
                                    style = franklin(FontWeight.Light, 11.5f, color = HN.Subtle),
                                )
                            }
                            BasicText(sourceLabel(slot.event?.source), style = franklin(FontWeight.Light, 11f, 1f, HN.Subtle))
                        }
                        HRule(HN.RuleSoft)
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val night = r.lastNight
            Tile(
                label = "Last night",
                value = {
                    if (night != null) HoursMinutes(night.total, 28f, 15f) else BasicText("—", style = caslon(28f, 1f))
                },
                caption = night?.let {
                    val inTarget = if (r.target.contains(it.total)) "inside target" else "outside target"
                    "${Formats.clock(it.bedtime.toLocalTime())} → ${Formats.clock(it.wake.toLocalTime())} · $inTarget"
                } ?: "No night yet",
                modifier = Modifier.weight(1f),
                onClick = onSleep,
            )
            val primary = r.meds.firstOrNull()
            Tile(
                label = primary?.let { "${it.schedule.name} streak" } ?: "Streak",
                value = {
                    BasicText(
                        buildAnnotatedString {
                            append(primary?.streakDays?.toString() ?: "—")
                            withStyle(SpanStyle(fontSize = 15.sp)) { append(if (primary?.streakDays == 1) " day" else " days") }
                        },
                        style = caslon(28f, 1f),
                    )
                },
                caption = primary?.let { "Best this year: ${it.bestStreakDays} days" } ?: "Add a medication to start one",
                modifier = Modifier.weight(1f),
            )
        }

        DocumentCard(state, onDocument)
    }
}

@Composable
private fun Hero(state: UiState) {
    val o = state.report.overall
    val total = state.report.meds.sumOf { it.slots.size }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BasicText(
                buildAnnotatedString {
                    append(o.percent?.toString() ?: "—")
                    if (o.percent != null) withStyle(SpanStyle(fontSize = 32.sp, letterSpacing = 0.em)) { append("%") }
                },
                style = caslon(74f, .82f, tracking = -.03f),
            )
            Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                BasicText(
                    "${Formats.monthName(state.report.month)} adherence",
                    style = franklin(FontWeight.Medium, 12f, 1.2f, tracking = .02f),
                )
                BasicText(
                    "${o.taken} of ${o.taken + o.missed} doses logged",
                    style = franklin(FontWeight.Light, 11f, 1.2f, HN.Muted),
                )
            }
        }
        Row(Modifier.fillMaxWidth().height(6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (o.taken > 0) Box(Modifier.weight(o.taken.toFloat()).height(6.dp).background(HN.Indigo, Radius2))
            if (o.missed > 0) Box(Modifier.weight(o.missed.toFloat()).height(6.dp).background(HN.TrackOff, Radius2))
        }
        // The mockup said "96 slots"; the real count comes from the schedules (74 in Sep 2026).
        val perSlip = if (total > 0) "%.1f".format(100.0 / total) else "0"
        BasicText(
            "${o.missed} missed · $total slots this month, so one slip costs about $perSlip points",
            style = franklin(FontWeight.Light, 10.5f, 1.4f, HN.Subtle),
        )
    }
}

@Composable
private fun Tile(
    label: String,
    value: @Composable () -> Unit,
    caption: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier
            .card()
            .let { if (onClick != null) it.tap(onClick = onClick) else it }
            .padding(horizontal = 15.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BasicText(label.uppercase(), style = Type.tileLabel)
        value()
        BasicText(caption, style = franklin(FontWeight.Light, 11f, 1.35f, HN.Muted))
    }
}

/** "7h 12m" with the unit letters set smaller, as in the mockup. */
@Composable
fun HoursMinutes(d: Duration, size: Float, unitSize: Float) {
    BasicText(
        buildAnnotatedString {
            append(d.toHours().toString())
            withStyle(SpanStyle(fontSize = unitSize.sp)) { append("h") }
            append(" %02d".format(d.toMinutes() % 60))
            withStyle(SpanStyle(fontSize = unitSize.sp)) { append("m") }
        },
        style = caslon(size, if (size > 40f) .85f else 1f, tracking = if (size > 40f) -.02f else 0f),
    )
}

@Composable
private fun DocumentCard(state: UiState, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(HN.DocCard, Radius3)
            .tap(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Column(
            Modifier
                .size(30.dp, 38.dp)
                .background(HN.Card)
                .border(1.dp, HN.RuleStrong)
                .padding(horizontal = 5.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
        ) {
            Box(Modifier.fillMaxWidth().height(1.5.dp).background(HN.ink(.35f)))
            Box(Modifier.fillMaxWidth().height(1.5.dp).background(HN.ink(.35f)))
            Box(Modifier.fillMaxWidth(.7f).height(1.5.dp).background(HN.ink(.2f)))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText("${Formats.monthName(state.report.month)} document", style = franklin(FontWeight.Medium, 13f))
            BasicText(
                "5 pages · geometry frozen for the month, so your ink stays put",
                style = franklin(FontWeight.Light, 11.5f, 1.3f, HN.Muted),
            )
        }
        BasicText("›", style = franklin(FontWeight.Normal, 14f, 1f, HN.Subtle))
    }
}

private fun nextDoseLine(state: UiState): String {
    val r = state.report
    val next = r.meds.flatMap { m -> m.slots.filter { it.state == SlotState.UPCOMING }.map { m to it } }
        .minByOrNull { it.second.slot.scheduledFor } ?: return ""
    val at = next.second.slot.scheduledFor
    return "Next dose ${Formats.weekday(at.dayOfWeek)} ${Formats.clock(at.toLocalTime())}, ${next.first.schedule.name}. "
}

private fun sourceLabel(source: DoseSource?): String = when (source) {
    DoseSource.WIDGET -> "Widget"
    DoseSource.NFC -> "NFC"
    DoseSource.NOTIFICATION -> "Notification"
    DoseSource.MANUAL -> "Manual"
    null -> ""
}

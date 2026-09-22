package io.github.couchknight.healthnote.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.couchknight.healthnote.app.ui.UiState
import io.github.couchknight.healthnote.app.ui.components.HRule
import io.github.couchknight.healthnote.app.ui.components.PrimaryButton
import io.github.couchknight.healthnote.app.ui.components.Radius2
import io.github.couchknight.healthnote.app.ui.components.SectionLabel
import io.github.couchknight.healthnote.app.ui.components.tap
import io.github.couchknight.healthnote.app.ui.theme.Franklin
import io.github.couchknight.healthnote.app.ui.theme.HN
import io.github.couchknight.healthnote.app.ui.theme.Type
import io.github.couchknight.healthnote.app.ui.theme.caslon
import io.github.couchknight.healthnote.app.ui.theme.franklin
import io.github.couchknight.healthnote.model.Formats
import io.github.couchknight.healthnote.model.NightSleep
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SleepScreen(state: UiState) {
    val r = state.report
    val last = r.lastNight
    Column(
        Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        if (last != null) LastNight(last)

        // Last 14 nights. Scale 0-10 h; the band is the target; a missing night is hatched.
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                SectionLabel("Last 14 nights", Modifier.weight(1f))
                BasicText(
                    "Target ${hours(r.target.minHours)}–${hours(r.target.maxHours)} h",
                    style = franklin(FontWeight.Light, 10.5f, 1f, HN.Subtle),
                )
            }
            val days = (13 downTo 0).map { r.today.minusDays(it.toLong()) }
            Box(Modifier.fillMaxWidth().height(104.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val scale = 10f
                    val bandTop = size.height * (1 - (r.target.maxHours.toFloat() / scale))
                    val bandBottom = size.height * (1 - (r.target.minHours.toFloat() / scale))
                    drawRect(HN.Indigo.copy(alpha = .09f), Offset(0f, bandTop), Size(size.width, bandBottom - bandTop))
                    val edge = HN.Indigo.copy(alpha = .18f)
                    drawLine(edge, Offset(0f, bandTop), Offset(size.width, bandTop), 1.dp.toPx())
                    drawLine(edge, Offset(0f, bandBottom), Offset(size.width, bandBottom), 1.dp.toPx())
                    val gap = 4.dp.toPx()
                    val w = (size.width - gap * (days.size - 1)) / days.size
                    days.forEachIndexed { i, d ->
                        val x = i * (w + gap)
                        val n = r.nights[d]
                        if (n == null) {
                            // Hatched full-height column: absence, not a short night.
                            var y = size.height
                            while (y > 0f) {
                                drawRect(HN.ink(.18f), Offset(x, y - 2.dp.toPx()), Size(w, 2.dp.toPx()))
                                y -= 6.dp.toPx()
                            }
                        } else {
                            val h = (n.total.seconds / 3600f / scale).coerceIn(0f, 1f) * size.height
                            val color = when {
                                d == last?.night -> HN.Indigo
                                n.total < Duration.ofHours(6) -> HN.SleepShort
                                else -> HN.SleepBar
                            }
                            drawRect(color, Offset(x, size.height - h), Size(w, h))
                        }
                    }
                    drawLine(HN.RuleStrong, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                }
            }
            val fmt = DateTimeFormatter.ofPattern("d MMM", Locale.UK)
            Row(Modifier.fillMaxWidth()) {
                BasicText(days.first().format(fmt), Modifier.weight(1f), style = franklin(FontWeight.Light, 10f, 1f, HN.Subtle))
                BasicText(days.last().format(fmt), style = franklin(FontWeight.Light, 10f, 1f, HN.Subtle))
            }
        }

        val s = r.sleep
        Column(Modifier.fillMaxWidth().background(HN.Rule), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                Stat("Month mean", s.mean?.let { Formats.hoursMinutes(it) } ?: "—", null, Modifier.weight(1f))
                Stat(
                    "Mean bed / wake",
                    if (s.meanBedtime != null && s.meanWake != null) "${Formats.clock(s.meanBedtime!!)} / ${Formats.clock(s.meanWake!!)}" else "—",
                    null, Modifier.weight(1f),
                )
            }
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                Stat("Best night", s.best?.let { Formats.hoursMinutes(it.total) } ?: "—", s.best?.let { Formats.dayLabel(it.night) }, Modifier.weight(1f))
                Stat("Worst night", s.worst?.let { Formats.hoursMinutes(it.total) } ?: "—", s.worst?.let { Formats.dayLabel(it.night) }, Modifier.weight(1f))
            }
        }

        Column {
            BasicText("NIGHT BY NIGHT", Modifier.padding(bottom = 10.dp), style = Type.sectionLabel.copy(fontSize = 10.sp))
            Row(Modifier.fillMaxWidth().padding(bottom = 7.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val head = franklin(FontWeight.SemiBold, 9.5f, 1f, HN.Subtle, tracking = .1f)
                BasicText("NIGHT", Modifier.width(52.dp), style = head)
                BasicText("BED → WAKE", Modifier.weight(1f), style = head)
                BasicText("TOTAL", Modifier.width(54.dp), style = head.copy(textAlign = TextAlign.End))
            }
            HRule(HN.RuleStrong)
            val through = s.through ?: r.today
            for (back in 0 until 7) {
                val d = through.minusDays(back.toLong())
                if (d.month != r.month.month) break
                val n = r.nights[d]
                val tone = if (n == null) HN.Subtle else HN.Ink
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BasicText(Formats.dayLabel(d), Modifier.width(52.dp), style = franklin(FontWeight.Normal, 12.5f, 1.2f))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        BasicText(
                            n?.let { "${Formats.clock(it.bedtime.toLocalTime())} → ${Formats.clock(it.wake.toLocalTime())}" } ?: "No session yet",
                            style = franklin(FontWeight.Light, 12.5f, 1.2f, tone),
                        )
                        if (n == null) {
                            BasicText(
                                "Samsung Health can lag a day; a watch night lands when the watch reconnects.",
                                style = franklin(FontWeight.Light, 10.5f, 1.3f, HN.Subtle),
                            )
                        }
                    }
                    BasicText(
                        n?.let { Formats.hoursColon(it.total) } ?: "—",
                        Modifier.width(54.dp),
                        style = caslon(13f, 1.2f, tone).copy(textAlign = TextAlign.End),
                    )
                }
                HRule(HN.RuleSoft)
            }
        }
    }
}

@Composable
private fun LastNight(n: NightSleep) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            HoursMinutes(n.total, 52f, 24f)
            BasicText(
                "${Formats.dayLabel(n.bedtime.toLocalDate())} → ${Formats.dayLabel(n.wake.toLocalDate())}\n" +
                    "${Formats.clock(n.bedtime.toLocalTime())} → ${Formats.clock(n.wake.toLocalTime())}",
                Modifier.padding(bottom = 5.dp),
                style = franklin(FontWeight.Light, 12f, 1.35f, HN.Muted),
            )
        }
        val stages = listOf(n.deep to HN.StageDeep, n.rem to HN.StageRem, (n.light + n.unstaged) to HN.StageLight, n.awake to HN.StageAwake)
        val total = stages.sumOf { it.first.seconds }.coerceAtLeast(1)
        Row(Modifier.fillMaxWidth().height(26.dp).clip(Radius2)) {
            for ((d, c) in stages) {
                if (d.seconds > 0) Box(Modifier.weight(d.seconds.toFloat() / total).fillMaxHeight().background(c))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val st = franklin(FontWeight.Light, 10.5f, 1.3f, HN.Muted)
            BasicText("Deep ${Formats.hoursColon(n.deep)}", style = st)
            BasicText("REM ${Formats.hoursColon(n.rem)}", style = st)
            BasicText("Light ${Formats.hoursColon(n.light + n.unstaged)}", style = st)
            BasicText("Awake ${Formats.hoursColon(n.awake)}", style = st)
        }
    }
}

@Composable
private fun Stat(label: String, value: String, suffix: String?, modifier: Modifier) {
    Column(modifier.fillMaxHeight().background(HN.Paper).padding(horizontal = 14.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        BasicText(label, style = franklin(FontWeight.Light, 10.5f, 1f, HN.Subtle))
        BasicText(
            buildAnnotatedString {
                append(value)
                if (suffix != null) {
                    withStyle(SpanStyle(fontFamily = Franklin, fontWeight = FontWeight.Light, fontSize = 11.sp, color = HN.Subtle)) {
                        append(" $suffix")
                    }
                }
            },
            style = caslon(19f, 1.1f),
        )
    }
}

private fun hours(h: Double) = if (h % 1.0 == 0.0) h.toInt().toString() else h.toString()

// Document ----------------------------------------------------------------------------------

@Composable
fun DocumentScreen(state: UiState, onSendNow: () -> Unit, onBatterySettings: () -> Unit) {
    val r = state.report
    val p = state.publish
    Column(
        Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
            PageThumbnail()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                BasicText(Formats.documentFileName(r.month), style = caslon(17f, 1.2f))
                val size = state.pdfBytes?.let { "${(it + 512) / 1024} KB" } ?: "rendering…"
                BasicText(
                    "1404 × 1872 · 5 pages · $size\nDocument/HealthNote/ on Supernote Cloud",
                    style = franklin(FontWeight.Light, 12f, 1.5f, HN.Muted),
                )
                BasicText(
                    "Regenerated daily. Upload skipped when the bytes are unchanged.",
                    Modifier.padding(top = 2.dp),
                    style = franklin(FontWeight.Light, 11f, 1.4f, HN.Subtle),
                )
            }
        }

        Column {
            HRule()
            val rows = listOf(
                "Last upload" to relative(p.lastUpload, r.now.toLocalDate()),
                "Next scheduled run" to relative(p.nextRun, r.now.toLocalDate()),
                "Pending dose change" to if (p.pendingChange) "Debounced, uploads in ~10 min" else "None",
                "Route" to p.route,
                "Ink safety" to "Geometry frozen for ${Formats.monthName(r.month)}",
            )
            for ((k, v) in rows) {
                Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.Top) {
                    BasicText(k, Modifier.weight(1f), style = franklin(FontWeight.Light, 12.5f, 1.3f, HN.Muted))
                    BasicText(v, style = franklin(FontWeight.Normal, 12.5f, 1.3f).copy(textAlign = TextAlign.End))
                }
                HRule(HN.RuleSoft)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(if (p.sentToDeviceAt != null) "Sent to INBOX ✓" else "Send to device now", height = 50.dp, onClick = onSendNow)
            BasicText(
                "Straight to the tablet over Wi-Fi, into INBOX. No overwrite there, so this is a courier, not the daily route.",
                style = Type.note,
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(HN.WarnBg)
                .drawBehind { drawRect(HN.Amber, size = Size(2.dp.toPx(), size.height)) }
                .padding(start = 17.dp, end = 15.dp, top = 14.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
                BasicText("The tablet pulls when it feels like it", style = franklin(FontWeight.Medium, 12.5f, 1.3f, HN.WarnTitle))
                BasicText(
                    "Auto Sync fires on wake, on Wi-Fi, and when you open Files. Expect the new page within hours of 06:00, not at 06:00.",
                    style = franklin(FontWeight.Light, 11.5f, 1.5f, HN.WarnBody),
                )
        }

        Column {
            HRule()
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    BasicText("Battery optimisation exempt", style = franklin(FontWeight.Medium, 13f, 1.2f))
                    BasicText("The usual reason the daily job silently stops", style = Type.small)
                }
                if (state.batteryExempt) {
                    BasicText("GRANTED", style = franklin(FontWeight.Medium, 11f, 1f, HN.Teal, tracking = .08f))
                } else {
                    BasicText(
                        "EXEMPT ›",
                        Modifier.tap(onClick = onBatterySettings),
                        style = franklin(FontWeight.Medium, 11f, 1f, HN.Amber, tracking = .08f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PageThumbnail() {
    Column(
        Modifier
            .size(74.dp, 99.dp)
            .background(HN.Card)
            .border(1.dp, HN.RuleStrong)
            .padding(horizontal = 7.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.fillMaxWidth(.6f).height(5.dp).background(HN.Ink))
        Column(Modifier.padding(top = 3.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val rows = listOf("1111101", "1101111", "1110000")
            for (row in rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (c in row) {
                        val color = when {
                            row == rows.last() && c == '0' -> Color(0xFFE5E0D3)
                            c == '1' -> HN.Ink
                            else -> HN.TrackOff
                        }
                        Box(Modifier.weight(1f).height(6.dp).background(color))
                    }
                }
            }
        }
        Box(Modifier.weight(1f))
        HRule(HN.ink(.2f))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(3) { Box(Modifier.weight(1f).height(4.dp).background(HN.TrackOff)) }
        }
    }
}

private fun relative(at: java.time.LocalDateTime, today: java.time.LocalDate): String {
    val day = when (at.toLocalDate()) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> Formats.dayMonthLabel(at.toLocalDate())
    }
    return "$day ${Formats.clock(at.toLocalTime())}"
}

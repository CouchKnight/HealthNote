package io.github.couchknight.healthnote.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.couchknight.healthnote.app.ui.components.BottomTabs
import io.github.couchknight.healthnote.app.ui.components.Header
import io.github.couchknight.healthnote.app.ui.components.MedRing
import io.github.couchknight.healthnote.app.ui.components.OutlineButton
import io.github.couchknight.healthnote.app.ui.components.PrimaryButton
import io.github.couchknight.healthnote.app.ui.components.Radius2
import io.github.couchknight.healthnote.app.ui.components.tap
import io.github.couchknight.healthnote.app.ui.screens.DocumentScreen
import io.github.couchknight.healthnote.app.ui.screens.EditScheduleScreen
import io.github.couchknight.healthnote.app.ui.screens.MedsScreen
import io.github.couchknight.healthnote.app.ui.screens.NfcScreen
import io.github.couchknight.healthnote.app.ui.screens.SleepScreen
import io.github.couchknight.healthnote.app.ui.screens.TodayScreen
import io.github.couchknight.healthnote.app.ui.theme.HN
import io.github.couchknight.healthnote.app.ui.theme.caslon
import io.github.couchknight.healthnote.app.ui.theme.franklin
import io.github.couchknight.healthnote.model.Formats
import kotlinx.coroutines.delay

private val SheetEasing = CubicBezierEasing(.2f, .8f, .2f, 1f)

@Composable
fun HealthNoteRoot(vm: HealthNoteViewModel) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    BackHandler(enabled = state.sheet != null || state.screen != Screen.TODAY) { vm.back() }
    LifecycleResumeEffect(Unit) {
        vm.refreshBattery()
        onPauseOrDispose { }
    }

    Box(Modifier.fillMaxSize().background(HN.Paper).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            val publish = state.publish
            Header(
                title = if (state.screen == Screen.EDIT && state.draft?.isNew == true) "New schedule" else state.screen.title,
                date = Formats.dayMonthLabel(state.report.today),
                canBack = state.screen.parent != null,
                syncLine = if (state.showSyncLine) {
                    "${Formats.documentFileName(state.report.month)} published ${Formats.clock(publish.lastUpload.toLocalTime())} " +
                        "· on the tablet within a few hours"
                } else {
                    null
                },
                onBack = { vm.back() },
            )

            // Each screen gets its own scroll position.
            key(state.screen) {
                Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    when (state.screen) {
                        Screen.TODAY -> TodayScreen(
                            state,
                            onDose = vm::openDose,
                            onSleep = { vm.go(Screen.SLEEP) },
                            onDocument = { vm.go(Screen.DOC) },
                        )
                        Screen.MEDS -> MedsScreen(state, onEdit = vm::editSchedule, onNfc = { vm.go(Screen.NFC) })
                        Screen.EDIT -> state.draft?.let {
                            EditScheduleScreen(it, state.report.month, vm::updateDraft, vm::saveDraft, vm::deleteDraft)
                        }
                        Screen.SLEEP -> SleepScreen(state)
                        Screen.DOC -> DocumentScreen(
                            state,
                            onSendNow = vm::sendNow,
                            onBatterySettings = {
                                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            },
                        )
                        Screen.NFC -> NfcScreen(state, onPick = vm::pickTag)
                    }
                }
            }

            BottomTabs(state.screen) { vm.go(it) }
        }

        // Confirm sheet over a scrim. The last dose stays on screen while the sheet slides out.
        val lastSheet = remember { mutableStateOf<PendingDose?>(null) }
        SideEffect { state.sheet?.let { lastSheet.value = it } }
        AnimatedVisibility(state.sheet != null, enter = fadeIn(tween(160)), exit = fadeOut(tween(120))) {
            Box(Modifier.fillMaxSize().background(HN.ink(.34f)).tap { vm.closeSheet() })
        }
        AnimatedVisibility(
            state.sheet != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(220, easing = SheetEasing)) { it },
            exit = slideOutVertically(tween(160)) { it },
        ) {
            (state.sheet ?: lastSheet.value)?.let {
                ConfirmSheet(it, onTaken = { vm.confirm(true) }, onSkip = { vm.confirm(false) }, onCancel = vm::closeSheet)
            }
        }

        state.toast?.let { toast ->
            LaunchedEffect(toast.id) {
                delay(2600)
                vm.dismissToast(toast.id)
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = 78.dp)
                    .fillMaxWidth()
                    .background(HN.Ink, Radius2)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                BasicText(toast.message, style = franklin(FontWeight.Normal, 12.5f, 1.4f, HN.Paper))
            }
        }
    }
}

@Composable
private fun ConfirmSheet(dose: PendingDose, onTaken: () -> Unit, onSkip: () -> Unit, onCancel: () -> Unit) {
    val s = dose.schedule
    Column(
        Modifier
            .fillMaxWidth()
            .background(HN.Paper)
            .drawBehind { drawRect(HN.ink(.14f), size = Size(size.width, 1.dp.toPx())) }
            .tap { } // swallow taps so they don't reach the scrim
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MedRing(HN.medColor(s.colorSlot), 34.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                BasicText(s.name, style = caslon(21f, 1.15f))
                BasicText(
                    "${s.dose} · scheduled ${Formats.clock(dose.slot.scheduledFor.toLocalTime())}, logging against that slot",
                    style = franklin(FontWeight.Light, 12f, 1.3f, HN.Muted),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            PrimaryButton("Taken", height = 52.dp, onClick = onTaken)
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlineButton("Skip today", Modifier.weight(1f), onClick = onSkip)
                Box(
                    Modifier.weight(1f).tap(onClick = onCancel),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText("Cancel", Modifier.padding(vertical = 16.dp), style = franklin(FontWeight.Normal, 14f, 1f, HN.Subtle))
                }
            }
        }
        BasicText(
            "Skipping counts as a resolved slot, not a miss, and the tablet shows a grey marker.",
            style = franklin(FontWeight.Light, 11f, 1.45f, HN.Subtle),
        )
    }
}
